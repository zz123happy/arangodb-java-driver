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
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.tcp.TcpClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static com.arangodb.next.ArangoDefaults.HEADER_SIZE;
import static io.netty.buffer.Unpooled.wrappedBuffer;
import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
class VstConnection implements ArangoConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(VstConnection.class);
    private static final byte[] PROTOCOL_HEADER = "VST/1.1\r\n\r\n".getBytes();

    private final ConnectionConfig config;

    private final HashMap<Long, Long> sendTimestamps = new HashMap<>();

    private final ConnectionProvider connectionProvider;
    private final MessageStore messageStore;

    private final String connectionName;
    private final ArangoTcpClient arangoTcpClient;

    private final AtomicLong mId = new AtomicLong();

    VstConnection(final ConnectionConfig config) {
        super();
        this.config = config;

        this.messageStore = new MessageStore();
        this.connectionProvider = initConnectionProvider();

        connectionName = "connection_" + System.currentTimeMillis() + "_" + Math.random();
        LOGGER.debug("Connection " + connectionName + " created");

        arangoTcpClient = new ArangoTcpClient();
    }

    private ConnectionProvider initConnectionProvider() {
        return ConnectionProvider.fixed(
                "tcp",
                1,
                config.getTimeout(),
                config.getTtl()
        );
    }

    public boolean isOpen() {
        return arangoTcpClient.isActive();
    }

    public void open() throws IOException {
        if (isOpen()) {
            return;
        }
        new Thread(arangoTcpClient::connect).start();
        // wait for connection
        try {
            arangoTcpClient.getConnectedFuture().get();
        } catch (InterruptedException e) {
            close();
        } catch (ExecutionException e) {
            throw new IOException(e.getCause());
        }
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        final long id = mId.incrementAndGet();
        ArangoMessage message = ArangoMessage.fromRequest(id, request);
        arangoTcpClient.send(message.writeChunked(config.getChunksize()));
        return messageStore.add(id);
    }

    public Mono<Void> close() {
        return arangoTcpClient.disconnect();
    }

    private class ArangoTcpClient {
        private volatile reactor.netty.Connection connection;
        private volatile CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
        private TcpClient tcpClient;
        private Chunk chunk;
        private ByteBuf chunkHeaderBuffer = IOUtils.createBuffer();
        private ByteBuf chunkContentBuffer = IOUtils.createBuffer();
        private final ChunkStore chunkStore;

        void send(ByteBuf buf) {
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

        ArangoTcpClient() {
            chunkStore = new ChunkStore(messageStore);

            tcpClient = applySslContext(TcpClient.create(connectionProvider))
                    .option(CONNECT_TIMEOUT_MILLIS, config.getTimeout())
                    .host(config.getHost().getHost())
                    .port(config.getHost().getPort())
                    .doOnDisconnected(c -> finalize(new IOException("Connection closed!")))
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
                    .doOnConnected(c -> {
                        connection = c;
                        connectedFuture.complete(null);
                        send(wrappedBuffer(PROTOCOL_HEADER));
                    });
        }

        private void handleError(final Throwable t) {
            t.printStackTrace();
            finalize(t);
            disconnect();
        }

        private void finalize(final Throwable t) {
            connectedFuture.completeExceptionally(t);
            connectedFuture = new CompletableFuture<>();
            chunkStore.clear();
            messageStore.clear(t);
            chunkHeaderBuffer.release();
            chunkContentBuffer.release();
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
                LOGGER.debug("Responsetime for Message " + chunk.getMessageId() + " is " + (sendTimestamps.get(chunk.getMessageId()) - System.currentTimeMillis()));
            }
        }

        private void readContent() {
            chunkStore.storeChunk(chunk, chunkContentBuffer);
            chunkHeaderBuffer.clear();
            chunkContentBuffer.clear();
            chunk = null;
        }

        void connect() {
            tcpClient
                    .connect()
                    .doOnError(this::handleError)
                    .subscribe();
        }

        Mono<Void> disconnect() {
            connection.dispose();
            return connection.onDispose();
        }

        boolean isActive() {
            return connection != null && connection.channel().isActive();
        }

        CompletableFuture<Void> getConnectedFuture() {
            return connectedFuture;
        }
    }
}
