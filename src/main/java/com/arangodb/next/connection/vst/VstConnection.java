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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.util.function.Supplier;

import static com.arangodb.next.ArangoDefaults.HEADER_SIZE;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
public class VstConnection implements ArangoConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(VstConnection.class);
    private static final String THREAD_PREFIX = "arango-vst";
    private static final byte[] PROTOCOL_HEADER = "VST/1.1\r\n\r\n".getBytes();

    private final ConnectionConfig config;
    private final MessageStore messageStore;
    private final ChunkStore chunkStore;
    private final MonoProcessor<ArangoConnection> readyConnection;
    private final Scheduler scheduler;
    private final TcpClient tcpClient;

    // state managed by scheduler thread arango-vst-X
    private long mId = 0L;
    private Throwable failureCause;
    private reactor.netty.Connection connection;

    // state managed by epoll tcp thread
    private Chunk chunk;
    private ByteBuf chunkHeaderBuffer = IOUtils.createBuffer();
    private ByteBuf chunkContentBuffer = IOUtils.createBuffer();

    public VstConnection(final ConnectionConfig config) {
        this.config = config;
        messageStore = new MessageStore();
        chunkStore = new ChunkStore(messageStore);
        readyConnection = MonoProcessor.create();
        scheduler = Schedulers.newSingle(THREAD_PREFIX);

        tcpClient = applySslContext(TcpClient.create(createConnectionProvider()))
                .option(CONNECT_TIMEOUT_MILLIS, config.getTimeout())
                .host(config.getHost().getHost())
                .port(config.getHost().getPort())
                .doOnDisconnected(c -> runOnScheduler(() -> finalize(new IOException("Connection closed!"))))
                .handle((i, o) -> {
                    i.receive()
                            .doOnNext(x -> {
                                while (x.readableBytes() > 0) {
                                    handleByteBuf(x);
                                }
                            })
                            .subscribe();
                    return Mono.never();
                })
                .doOnConnected(c -> subscribeOnScheduler(() -> {
                            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
                            connection = c;
                            send(wrappedBuffer(PROTOCOL_HEADER));
                            return authenticate();
                        })
                                .doOnSuccess((v) -> readyConnection.onNext(this))
                                .doOnError(this::handleError)
                                .subscribe()
                );
    }

    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.fixed(
                "tcp",
                1,  // TODO: test with more connections
                config.getTimeout(),
                config.getTtl()
        );
    }

    @Override
    public Mono<ArangoConnection> initialize() {
        new Thread(this::connect).start();
        return readyConnection;
    }

    private Mono<Void> authenticate() {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";

        if (config.getAuthenticationMethod().isPresent()) {
            AuthenticationMethod authenticationMethod = config.getAuthenticationMethod().get();
            final long id = mId++;
            return execute(id, RequestConverter.encodeBuffer(id, authenticationMethod.getVstAuthenticationMessage(), config.getChunksize()))
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
            send(buf);
            return responseMono;
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

    private Disposable runOnScheduler(Runnable task) {
        return scheduler.schedule(task);
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        return subscribeOnScheduler(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            final long id = mId++;
            return execute(id, RequestConverter.encodeRequest(id, request, config.getChunksize()));
        });
    }

    private void send(ByteBuf buf) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
        connection.outbound()
                .send(Mono.just(buf))
                .then()
                .doOnError(this::handleError)
                .subscribe();
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
        close();
    }

    private void finalize(final Throwable t) {
        assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";

        failureCause = t;
        chunkStore.clear();
        messageStore.clear(t);
        chunkHeaderBuffer.release();
        chunkContentBuffer.release();
        if (!readyConnection.isTerminated()) {
            readyConnection.onError(t);
        }
    }

    private void readBytes(ByteBuf bbIn, ByteBuf out, int len) {
        int bytesToRead = Integer.min(len, bbIn.readableBytes());
        out.ensureWritable(bytesToRead);
        bbIn.readBytes(out, bytesToRead);
    }

    private void handleByteBuf(ByteBuf bbIn) {
        // new chunk
        if (chunk == null) {
            int missingHeaderBytes = HEADER_SIZE - chunkHeaderBuffer.readableBytes();
            readBytes(bbIn, chunkHeaderBuffer, missingHeaderBytes);
            if (chunkHeaderBuffer.readableBytes() == HEADER_SIZE) {
                readHeader();
            }
        }

        if (chunk != null) {
            int missingContentBytes = chunk.getContentLength() - chunkContentBuffer.readableBytes();
            readBytes(bbIn, chunkContentBuffer, missingContentBytes);

            // chunkContent completely received
            if (chunkContentBuffer.readableBytes() == chunk.getContentLength()) {
                readContent();
            }
        }
    }

    private void readHeader() {
        final int chunkLength = chunkHeaderBuffer.readIntLE();
        final int chunkX = chunkHeaderBuffer.readIntLE();

        final long messageId = chunkHeaderBuffer.readLongLE();
        final long messageLength = chunkHeaderBuffer.readLongLE();
        final int contentLength = chunkLength - HEADER_SIZE;

        chunk = new Chunk(messageId, chunkX, messageLength, 0, contentLength);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(String.format("Received chunk %s:%s from message %s", chunk.getChunk(), chunk.isFirstChunk() ? 1 : 0, chunk.getMessageId()));
        }
    }

    private void readContent() {
        // create defensive copies to be forwarded to the scheduler thread
        Chunk chunkCopy = chunk.copy();
        ByteBuf chunkContentBufferCopy = IOUtils.copyOf(chunkContentBuffer);
        runOnScheduler(() -> chunkStore.storeChunk(chunkCopy, chunkContentBufferCopy));

        chunkHeaderBuffer.clear();
        chunkContentBuffer.clear();
        chunk = null;
    }

    private void connect() {
        tcpClient
                .connect()
                .doOnError(t -> runOnScheduler(() -> handleError(t)))
                .subscribe();
    }

    @Override
    public Mono<Void> close() {
        return subscribeOnScheduler(() -> {
            assert Thread.currentThread().getName().startsWith(THREAD_PREFIX) : "Wrong thread!";
            connection.dispose();
            return connection.onDispose();
        });
    }

//    boolean isActive() {
//        // TODO: runOnScheduler
//        return connection != null && connection.channel().isActive();
//    }

}
