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
import com.arangodb.next.entity.ImmutableClusterEndpoints;
import com.arangodb.next.entity.codec.ArangoSerializer;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.*;

/**
 * @author Michele Rastelli
 */
public class AcquireHostListMockTest {

    private static final HostDescription initialHost = HostDescription.of("initialHost", 8529);

    private final CommunicationConfig config = CommunicationConfig.builder()
            .addHosts(initialHost)
            .connectionsPerHost(10)
            .build();

    static abstract class MockConnectionFactory implements ConnectionFactory {

        abstract List<HostDescription> getHosts();

        protected void stubIsConnected(ArangoConnection connection, HostDescription host) {
            when(connection.isConnected()).thenReturn(Mono.just(true));
        }

        protected Mono<ArangoResponse> getClusterEndpointsResponse() {
            byte[] responseBody = ArangoSerializer.of(ContentType.VPACK).serialize(
                    ImmutableClusterEndpoints.builder()
                            .error(false)
                            .code(200)
                            .endpoints(getHosts().stream()
                                    .map(it -> Collections.singletonMap("endpoint", "tcp://" + it.getHost() + ":" + it.getPort()))
                                    .collect(Collectors.toList()))
                            .build()
            );

            return Mono.just(
                    ArangoResponse.builder()
                            .responseCode(200)
                            .body(IOUtils.createBuffer().writeBytes(responseBody))
                            .build()
            );
        }

        protected void stubRequestClusterEndpoints(ArangoConnection connection) {
            when(connection.execute(any(ArangoRequest.class))).thenReturn(getClusterEndpointsResponse());
        }

        @Override
        public Mono<ArangoConnection> create(HostDescription host, AuthenticationMethod authentication) {
            ArangoConnection connection = mock(ArangoConnection.class);
            stubIsConnected(connection, host);
            stubRequestClusterEndpoints(connection);
            when(connection.close()).thenReturn(Mono.empty());
            return Mono.just(connection);
        }

        @Override
        public void close() {
        }

    }

    @Test
    void newHosts() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Arrays.asList(
                        HostDescription.of("host1", 1111),
                        HostDescription.of("host2", 2222)
                );
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        communication.initialize().block();

        assertThat(communication.getConnectionsByHost().keySet()).containsExactlyInAnyOrder(factory.getHosts().toArray(new HostDescription[0]));
        communication.getConnectionsByHost().values().forEach(connections -> assertThat(connections).hasSize(10));
    }

    @Test
    void sameHost() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Collections.singletonList(initialHost);
            }
        };
        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        communication.initialize().block();

        assertThat(communication.getConnectionsByHost().keySet()).containsExactly(factory.getHosts().toArray(new HostDescription[0]));
        communication.getConnectionsByHost().values().forEach(connections -> assertThat(connections).hasSize(10));
    }


    @Test
    void allUnreachableHosts() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Arrays.asList(
                        HostDescription.of("host1", 1111),
                        HostDescription.of("host2", 2222)
                );
            }

            @Override
            protected void stubIsConnected(ArangoConnection connection, HostDescription host) {
                when(connection.isConnected()).thenReturn(Mono.just(false));
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        Throwable thrown = catchThrowable(() -> communication.initialize().block());
        assertThat(Exceptions.unwrap(thrown))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Could not create any connection");

        assertThat(communication.getConnectionsByHost().keySet()).isEmpty();
    }

    @Test
    void allUnreachableAcquiredHosts() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Arrays.asList(
                        HostDescription.of("host1", 1111),
                        HostDescription.of("host2", 2222)
                );
            }

            @Override
            protected void stubIsConnected(ArangoConnection connection, HostDescription host) {
                if (getHosts().contains(host)) {
                    when(connection.isConnected()).thenReturn(Mono.just(false));
                } else {
                    when(connection.isConnected()).thenReturn(Mono.just(true));
                }
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        Throwable thrown = catchThrowable(() -> communication.initialize().block());
        assertThat(Exceptions.unwrap(thrown))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Could not create any connection");

        assertThat(communication.getConnectionsByHost().keySet()).isEmpty();
        communication.updateHostList().block();

        // FIXME: this should be empty
        assertThat(communication.getConnectionsByHost().keySet()).containsExactly(initialHost);
        communication.getConnectionsByHost().values().forEach(connections -> assertThat(connections).hasSize(10));
    }

    @Test
    void someUnreachableAcquiredHosts() {
        List<HostDescription> unreachableHosts = Collections.singletonList(HostDescription.of("host1", 1111));
        List<HostDescription> reachableHosts = Collections.singletonList(HostDescription.of("host2", 2222));

        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Stream.concat(unreachableHosts.stream(), reachableHosts.stream()).collect(Collectors.toList());
            }

            @Override
            protected void stubIsConnected(ArangoConnection connection, HostDescription host) {
                if (unreachableHosts.contains(host)) {
                    when(connection.isConnected()).thenReturn(Mono.just(false));
                } else {
                    when(connection.isConnected()).thenReturn(Mono.just(true));
                }
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        communication.initialize().block();

        assertThat(communication.getConnectionsByHost().keySet()).containsExactly(reachableHosts.toArray(new HostDescription[0]));
        communication.getConnectionsByHost().values().forEach(connections -> assertThat(connections).hasSize(10));
    }

    @Test
    void acquireHostListRequestError() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Arrays.asList(
                        HostDescription.of("host1", 1111),
                        HostDescription.of("host2", 2222)
                );
            }

            @Override
            protected void stubRequestClusterEndpoints(ArangoConnection connection) {
                when(connection.execute(any(ArangoRequest.class))).thenReturn(Mono.just(
                        ArangoResponse.builder()
                                .responseCode(500)
                                .build()
                ));
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        Throwable thrown = catchThrowable(() -> communication.initialize().block());
        assertThat(Exceptions.unwrap(thrown))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Could not create any connection");

        assertThat(communication.getConnectionsByHost().keySet()).isEmpty();
    }

    @Test
    void acquireHostListWithSomeRequestError() {
        MockConnectionFactory factory = new MockConnectionFactory() {
            @Override
            List<HostDescription> getHosts() {
                return Arrays.asList(
                        HostDescription.of("host1", 1111),
                        HostDescription.of("host2", 2222)
                );
            }

            @Override
            protected void stubRequestClusterEndpoints(ArangoConnection connection) {
                when(connection.execute(any(ArangoRequest.class)))
                        .thenReturn(Mono.just(
                                ArangoResponse.builder()
                                        .responseCode(500)
                                        .body(Unpooled.EMPTY_BUFFER)
                                        .build()
                        ))
                        .thenReturn(getClusterEndpointsResponse());
            }
        };

        ArangoCommunicationImpl communication = new ArangoCommunicationImpl(config, factory);
        communication.initialize().block();

        assertThat(communication.getConnectionsByHost().keySet()).containsExactlyInAnyOrder(factory.getHosts().toArray(new HostDescription[0]));
        communication.getConnectionsByHost().values().forEach(connections -> assertThat(connections).hasSize(10));
    }

}
