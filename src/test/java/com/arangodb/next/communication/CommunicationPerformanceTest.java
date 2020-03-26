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


package com.arangodb.next.communication;

import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.HostDescription;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Date;

import static com.arangodb.next.connection.ConnectionTestUtils.VERSION_REQUEST;

/**
 * @author Michele Rastelli
 */
class CommunicationPerformanceTest {

    private final HostDescription host = HostDescription.of("127.0.0.1", 8529);
    private final AuthenticationMethod authentication = AuthenticationMethod.ofBasic("root", "test");
    private final CommunicationConfig config = CommunicationConfig.builder()
            .addHosts(host)
            .authenticationMethod(authentication)
            .connectionsPerHost(4)
            .acquireHostList(false)
            .build();

    @Test
    @SuppressWarnings("squid:S2699")
        // Tests should include assertions
    void infiniteParallelLoop() {
        int requests = 1_000_000;
        long start = new Date().getTime();

        ArangoCommunication.create(config).flatMapMany(communication ->
                Flux
                        .range(1, requests)
                        .flatMap(i -> communication.execute(VERSION_REQUEST))
        ).then().block();

        long end = new Date().getTime();
        long elapsed = end - start;
        System.out.println("rate: " + (1_000.0 * requests / elapsed));
    }

}
