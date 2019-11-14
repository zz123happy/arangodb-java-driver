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

import java.util.stream.Stream;

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
        ArangoConnection.create(ArangoProtocol.VST, config)
                .flatMapMany(connection -> Flux.fromStream(Stream.iterate(0, i -> i + 1))
                        .flatMap(i -> connection.execute(getRequest))
                        .doOnNext(v -> {
                            new VPackSlice(IOUtilsTest.getByteArray(v.getBody()));
                            v.getBody().release();
                        }))
                .then().block();
    }


}
