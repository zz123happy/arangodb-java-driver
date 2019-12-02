/*
 * DISCLAIMER
 *
 * Copyright 2016 ArangoDB GmbH, Cologne, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright holder is ArangoDB GmbH, Cologne, Germany
 */

package com.arangodb.next.connection.vst;

import com.arangodb.next.connection.*;
import com.arangodb.next.connection.exceptions.ArangoConnectionAuthenticationException;
import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.netty.Connection;
import reactor.netty.DisposableChannel;
import reactor.netty.channel.AbortedException;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static com.arangodb.next.connection.ConnectionSchedulerFactory.THREAD_PREFIX;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
final public class VstConnection implements ArangoConnection {

    private static final Logger log = LoggerFactory.getLogger(VstConnection.class);

    private static final byte[] PROTOCOL_HEADER = "VST/1.1\r\n\r\n".getBytes();

    private volatile boolean initialized = false;
    private volatile boolean closing = false;

    private final HostDescription host;
    @Nullable
    private final AuthenticationMethod authentication;
    private final ConnectionConfig config;
    private final MessageStore messageStore;
    private final Scheduler scheduler;
    private final VstReceiver vstReceiver;

    // state managed by scheduler thread arango-vst-X
    private long mId = 0L;
    private MonoProcessor<Connection> session;

    // mono that will be resolved when the closing process is finished
    private final MonoProcessor<Void> closed;
    private ConnectionState connectionState = ConnectionState.DISCONNECTED;

    private enum ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    public VstConnection(final HostDescription host,
                         @Nullable final AuthenticationMethod authentication,
                         final ConnectionConfig config,
                         final ConnectionSchedulerFactory schedulerFactory) {
        log.debug("VstConnection({})", config);
        this.host = host;
        this.authentication = authentication;
        this.config = config;
        closed = MonoProcessor.create();
        messageStore = new MessageStore();
        scheduler = schedulerFactory.getScheduler();
        vstReceiver = new VstReceiver(messageStore::resolve);
    }

    @Override
    public synchronized Mono<ArangoConnection> initialize() {
        log.debug("initialize()");
        if (initialized) {
            throw new IllegalStateException("Already initialized!");
        }
        initialized = true;
        return publishOnScheduler(this::connect).timeout(Duration.ofMillis(config.getTimeout())).map(it -> this);
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        log.debug("execute({})", request);
        return publishOnScheduler(this::connect).flatMap(c -> {
            final long id = increaseAndGetMessageCounter();
            return execute(c, id, RequestConverter.encodeRequest(id, request, config.getChunkSize()));
        }).timeout(Duration.ofMillis(config.getTimeout()));
    }

    @Override
    public synchronized Mono<Void> close() {
        if (closing) {
            return Mono.error(new IllegalStateException("Connection has been already closed!"));
        }
        closing = true;

        return publishOnScheduler(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            log.debug("close()");
            if (connectionState == ConnectionState.DISCONNECTED) {
                return publishOnScheduler(vstReceiver::shutDown);
            } else {
                return session
                        .doOnNext(DisposableChannel::dispose)
                        .flatMap(DisposableChannel::onDispose)
                        .publishOn(scheduler)
                        .doFinally(s -> vstReceiver.shutDown())
                        .then(closed);
            }
        });
    }

    private long increaseAndGetMessageCounter() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return ++mId;
    }

    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.fixed(
                "tcp",
                1,
                config.getTimeout(),
                config.getTtl()
        );
    }

    private Mono<Void> authenticate(Connection connection) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        log.debug("authenticate()");
        return Optional.ofNullable(authentication)
                .map(authenticationMethod -> {
                    final long id = increaseAndGetMessageCounter();
                    final ByteBuf buffer = RequestConverter.encodeBuffer(
                            id,
                            authenticationMethod.getVstAuthenticationMessage(),
                            config.getChunkSize()
                    );
                    return execute(connection, id, buffer)
                            .doOnNext(response -> {
                                log.debug("in authenticate(): received response {}", response);
                                if (response.getResponseCode() != 200) {
                                    log.debug("in authenticate(): throwing ArangoConnectionAuthenticationException()");
                                    throw ArangoConnectionAuthenticationException.of(response);
                                }
                            })
                            .then();
                })
                .orElse(Mono.empty());
    }

    private Mono<ArangoResponse> execute(final Connection connection, long id, final ByteBuf buf) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return send(connection, buf).then(messageStore.addRequest(id));
    }

    /**
     * Executes the provided task in the scheduler.
     *
     * @param task task to execute
     * @param <T>  type returned
     * @return the supplied mono
     */
    private <T> Mono<T> publishOnScheduler(Supplier<Mono<T>> task) {
        return Mono.defer(task).subscribeOn(scheduler);
    }

    private Mono<Void> publishOnScheduler(Runnable task) {
        return Mono.defer(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            task.run();
            return Mono.empty();
        }).then().subscribeOn(scheduler);
    }

    private Mono<Void> send(final Connection connection, final ByteBuf buf) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return connection.outbound()
                .send(Mono.just(buf))
                .then()
                .onErrorMap(e -> e.getClass().getSimpleName().equals("InternalNettyException"), Throwable::getCause)
                .onErrorMap(AbortedException.class, e -> new IOException(e.getCause()))
                .doOnError(t -> {
                    log.atDebug().addArgument(() -> t.getClass().getSimpleName()).log("send(ByteBuf)#doOnError({})");
                    handleError(t);
                });
    }

    private TcpClient applySslContext(TcpClient httpClient) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return config.getSslContext()
                .filter(v -> config.getUseSsl())
                .map(sslContext ->
                        httpClient.secure(spec ->
                                spec.sslContext(new JdkSslContext(sslContext, true, ClientAuth.NONE))))
                .orElse(httpClient);
    }

    private void handleError(final Throwable t) {
        log.atDebug().addArgument(() -> t.getClass().getSimpleName()).log("handleError({})");
        publishOnScheduler(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            if (connectionState == ConnectionState.DISCONNECTED) {
                return;
            }
            connectionState = ConnectionState.DISCONNECTED;
            vstReceiver.clear();
            messageStore.clear(t);
            mId = 0L;
            session.cancel();
            session = null;

            // completes the closing process
            if (closing) {
                closed.onComplete();
            }

        }).subscribe();
    }

    /**
     * @return a Mono that will be resolved with a ready to use connection (connected, initialized and authenticated)
     */
    private Mono<? extends Connection> connect() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        log.debug("connect()");

        if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.CONNECTING) {
            return session;
        } else if (connectionState == ConnectionState.DISCONNECTED) {
            // crate a pending session
            session = MonoProcessor.create();
            connectionState = ConnectionState.CONNECTING;
            return createTcpClient()
                    .connect()
                    .publishOn(scheduler)
                    .flatMap(c -> send(c, wrappedBuffer(PROTOCOL_HEADER)).then(authenticate(c)).thenReturn(c))
                    .publishOn(scheduler)
                    .doOnNext(this::setSession);
        } else {
            throw new IllegalStateException("connectionState: " + connectionState);
        }
    }

    private TcpClient createTcpClient() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return applySslContext(TcpClient.create(createConnectionProvider()))
                .option(CONNECT_TIMEOUT_MILLIS, config.getTimeout())
                .host(host.getHost())
                .port(host.getPort())
                .doOnDisconnected(c -> handleError(new IOException("Connection closed!")))
                .handle((inbound, outbound) -> inbound
                        .receive()
                        // creates a defensive copy of the buffer to be propagate to the scheduler thread
                        .map(IOUtils::copyOf)
                        .publishOn(scheduler)
                        .doOnNext(vstReceiver::handleByteBuf)
                        .then()
                );
    }

    private void setSession(Connection connection) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        connectionState = ConnectionState.CONNECTED;
        session.onNext(connection);
    }

}
