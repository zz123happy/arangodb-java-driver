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
import io.netty.buffer.ByteBuf;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.Connection;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.util.function.Supplier;

import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
final public class VstConnection implements ArangoConnection {

    static final String THREAD_PREFIX = "arango-vst";
    private static final byte[] PROTOCOL_HEADER = "VST/1.1\r\n\r\n".getBytes();

    private final ConnectionConfig config;
    private final MessageStore messageStore;
    private final Scheduler scheduler;
    private final TcpClient tcpClient;
    private final VstReceiver vstReceiver;

    // state managed by scheduler thread arango-vst-X
    private long mId = 0L;
    private Throwable failureCause;
    private reactor.netty.Connection connection;

    public VstConnection(final ConnectionConfig config) {
        this.config = config;
        messageStore = new MessageStore();
        scheduler = Schedulers.newSingle(THREAD_PREFIX);
        vstReceiver = new VstReceiver(messageStore::resolve);

        tcpClient = applySslContext(TcpClient.create(createConnectionProvider()))
                .option(CONNECT_TIMEOUT_MILLIS, config.getTimeout())
                .host(config.getHost().getHost())
                .port(config.getHost().getPort())
                .doOnDisconnected(c -> subscribeOnScheduler(() -> finalize(new IOException("Connection closed!"))).subscribe())
                .handle((inbound, outbound) -> inbound
                        .receive()
                        // creates a defensive copy of the buffer to be propagate to the scheduler thread
                        .map(IOUtils::copyOf)
                        .publishOn(scheduler)
                        .doOnNext(vstReceiver::handleByteBuf)
                        .then()
                );
    }

    @Override
    public Mono<ArangoConnection> initialize() {
        return connect().map(it -> this);
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        return subscribeOnScheduler(() -> {
            final long id = increaseAndGetMessageCounter();
            return execute(id, RequestConverter.encodeRequest(id, request, config.getChunkSize()));
        });
    }

    @Override
    public Mono<Void> close() {
        return subscribeOnScheduler(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            connection.dispose();
            return connection.onDispose();
        });
    }

    private long increaseAndGetMessageCounter() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return ++mId;
    }

    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.fixed(
                "tcp",
                1,  // TODO: test with more connections
                config.getTimeout(),
                config.getTtl()
        );
    }

    private Mono<Void> authenticate() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";

        if (config.getAuthenticationMethod().isPresent()) {
            AuthenticationMethod authenticationMethod = config.getAuthenticationMethod().get();
            final long id = increaseAndGetMessageCounter();
            return execute(id, RequestConverter.encodeBuffer(id, authenticationMethod.getVstAuthenticationMessage(), config.getChunkSize()))
                    .doOnNext(response -> {
                        if (response.getResponseCode() != 200) {
                            throw new RuntimeException("Authentication failure!");
                        }
                    })
                    .then();
        } else {
            return Mono.empty();
        }
    }

    private Mono<ArangoResponse> execute(long id, ByteBuf buf) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";

        if (failureCause != null) {
            return Mono.error(failureCause);
        } else {
            Mono<ArangoResponse> responseMono = messageStore.add(id);
            return send(buf).then(responseMono);
        }
    }

    /**
     * Executes the provided task in the scheduler.
     *
     * @param task task to execute
     * @param <T>  type returned
     * @return the supplied mono
     */
    private <T> Mono<T> subscribeOnScheduler(Supplier<Mono<T>> task) {
        return Mono.defer(task).subscribeOn(scheduler);
    }

    private Mono<Void> subscribeOnScheduler(Runnable task) {
        return Mono.defer(() -> {
            task.run();
            return Mono.empty();
        }).then().subscribeOn(scheduler);
    }

    private Mono<Void> send(ByteBuf buf) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        return connection.outbound()
                .send(Mono.just(buf))
                .then()
                .doOnError(this::handleError);
    }

    private TcpClient applySslContext(TcpClient httpClient) {
        return config.getSslContext()
                .filter(v -> config.getUseSsl())
                .map(sslContext ->
                        httpClient.secure(spec ->
                                spec.sslContext(new JdkSslContext(sslContext, true, ClientAuth.NONE))))
                .orElse(httpClient);
    }

    private void handleError(final Throwable t) {
        t.printStackTrace();
        finalize(t);
        close().subscribe();
    }

    private void finalize(final Throwable t) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";

        vstReceiver.close();
        failureCause = t;
        messageStore.clear(t);
    }

    private Mono<? extends Connection> connect() {
        return tcpClient
                .connect()
                .publishOn(scheduler)
                .doOnNext(this::setConnection)
                .flatMap(c -> send(wrappedBuffer(PROTOCOL_HEADER)).then(authenticate()).thenReturn(c))
                .doOnError(this::handleError);
    }

    private void setConnection(Connection connection) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        this.connection = connection;
    }

}
