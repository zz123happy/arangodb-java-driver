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

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.DisposableServer;
import reactor.netty.http.client.HttpClient;

/**
 * @author Michele Rastelli
 */
class EchoTest {

    @Test
    void echoHttpTest() throws InterruptedException {
        DisposableServer server = new EchoHttpServer().start().join();

        HttpClient.create()
                .post()
                .uri("127.0.0.1:9000")
                .send(ByteBufFlux.fromString(Mono.just("hello")))
                .response((meta, content) -> {
                    System.out.println(meta.responseHeaders());
                    return content.asString().doOnNext(System.out::println);
                })
                .subscribe();

        Thread.sleep(100);
        server.dispose();
        server.onDispose().block();
    }

}
