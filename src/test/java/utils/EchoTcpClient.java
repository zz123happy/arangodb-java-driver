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

package utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;


/**
 * @author Michele Rastelli
 */

class EchoTcpClient {
    private static Logger LOGGER = LoggerFactory.getLogger(EchoTcpClient.class);

    private CompletableFuture<Connection> connection = new CompletableFuture<>();

    public void send(String message) {
        connection.thenApply(c -> c.outbound()
                .sendString(Mono.just(message))
                .then()
                .subscribe()
        );
    }

    public CompletableFuture<Connection> start() {
                TcpClient.create()
                        .host("127.0.0.1")
                        .port(9000)
                        .doOnConnected((c) -> connection.complete(c))
                        .handle((i, o) -> {
                            i.receive()
                                    .asString(StandardCharsets.UTF_8)
                                    .doOnNext(LOGGER::info)
                                    .doOnError(it -> LOGGER.error(it.getMessage(), it))
                                    .subscribe();

                            return Mono.never();
                        })
                        .connect()
                        .subscribe();

        return connection;
    }
}
