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


import com.arangodb.next.connection.*;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

/**
 * @author Michele Rastelli
 */
public class AcquireHostListMockTest {

    private final CommunicationConfig config = CommunicationConfig.builder()
            .addHosts(HostDescription.of("mockHost", 8529))
            .build();

    static class MockConnectionFactory implements ConnectionFactory {

        @Override
        public Mono<ArangoConnection> create(HostDescription host, AuthenticationMethod authentication) {
            ArangoConnection connection = mock(ArangoConnection.class);
            when(connection.isConnected()).thenReturn(Mono.just(true));
            when(connection.close()).thenReturn(Mono.empty());
            when(connection.execute(any(ArangoRequest.class))).thenReturn(Mono.just(
                    ArangoResponse.builder()
                            .responseCode(200)
                            .body(
                                    IOUtils.createBuffer()
                                            .writeBytes(("{" +
                                                    "\"error\": false," +
                                                    "\"code\": 200," +
                                                    "\"endpoints\": [" +
                                                    "{" +
                                                    "\"endpoint\": \"tcp://mockHost:8529\"" +
                                                    "}" +
                                                    "]" +
                                                    "}").getBytes(StandardCharsets.UTF_8))
                            )
                            .build()
            ));
            return Mono.just(connection);
        }

        @Override
        public void close() {
        }
    }

    @Test
    void acquireNewHosts() {
        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, new MockConnectionFactory());
        communication.initialize().block();

        System.out.println(communication.getConnectionsByHost().keySet());
    }

}
