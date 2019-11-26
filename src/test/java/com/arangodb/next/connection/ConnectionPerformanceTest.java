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


package com.arangodb.next.connection;

import com.arangodb.velocypack.VPackSlice;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Michele Rastelli
 */
@Disabled
class ConnectionPerformanceTest {

    private final ConnectionConfig config = ConnectionConfig.builder()
            .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
            .host(HostDescription.of("172.28.3.1", 8529))
            .build();

    private final ArangoRequest getRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/version")
            .requestType(ArangoRequest.RequestType.GET)
            .build();

    @Test
    void inifiniteParallelLoop() {
        int requests = 1_000_000;
        int connections = 4;
        long start = new Date().getTime();

        IntStream.range(0, connections)
                .mapToObj(i -> requestBatch(requests / connections))
                .collect(Collectors.toList())
                .forEach(CompletableFuture::join);

        long end = new Date().getTime();
        long elapsed = end - start;
        System.out.println("rate: " + (1_000.0 * requests / elapsed));
    }

    private CompletableFuture<Void> requestBatch(int requests) {
        return ArangoConnection.create(ArangoProtocol.VST, config)
                .flatMapMany(connection -> Flux.range(0, requests)
                        .doOnNext(i -> {
                            if (i % 100_000 == 0)
                                System.out.println(i);
                        })
                        .flatMap(i -> connection.execute(getRequest))
                        .doOnNext(v -> new VPackSlice(IOUtilsTest.getByteArray(v.getBody())).get("server"))
                        .doOnNext(v -> v.getBody().release()))
                .then().toFuture();
    }

}
