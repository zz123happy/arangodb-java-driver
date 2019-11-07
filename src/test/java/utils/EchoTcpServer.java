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

import reactor.netty.tcp.TcpServer;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * @author Michele Rastelli
 */
public class EchoTcpServer {

    public CompletableFuture<Void> start() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        new Thread(() -> TcpServer.create()
                .host("0.0.0.0")
                .port(9000)
                .doOnBound((c) -> done.complete(null))
                .handle((inbound, outbound) -> outbound.sendByteArray(inbound.receive().asByteArray()
                        .filter(bytes -> !Arrays.equals(bytes, "VST/1.1\r\n\r\n".getBytes()))).neverComplete())
                        .bindNow().onDispose().block()).start();

        return done;
    }

}
