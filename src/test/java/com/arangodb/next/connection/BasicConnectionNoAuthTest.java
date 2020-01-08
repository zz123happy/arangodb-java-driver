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

import deployments.ContainerDeployment;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

import java.io.IOException;

import static com.arangodb.next.connection.ConnectionTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class BasicConnectionNoAuthTest {

    private static HostDescription host;

    private final ConnectionConfig config;

    BasicConnectionNoAuthTest() {
        config = ConnectionConfig.builder()
                .build();
    }

    @Container
    private static final ContainerDeployment deployment = ContainerDeployment.ofSingleServerNoAuth();

    @BeforeAll
    static void setup() {
        host = deployment.getHosts().get(0);
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void getRequest(ArangoProtocol protocol) {
        ArangoConnection connection = new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY)
                .create(host, null).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(ConnectionTestUtils.versionRequest).block();
        verifyGetResponseVPack(response);
        connection.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void postRequest(ArangoProtocol protocol) {
        ArangoConnection connection = new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY)
                .create(host, null).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(ConnectionTestUtils.postRequest()).block();
        verifyPostResponseVPack(response);
        connection.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void parallelLoop(ArangoProtocol protocol) {
        new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY).create(host, deployment.getAuthentication())
                .flatMapMany(c ->
                        Flux.range(0, 1_000)
                                .flatMap(i -> c.execute(ConnectionTestUtils.versionRequest))
                                .doOnNext(ConnectionTestUtils::verifyGetResponseVPack))
                .then().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void wrongHostFailure(ArangoProtocol protocol) {
        HostDescription wrongHost = HostDescription.of("wrongHost", 8529);
        Throwable thrown = catchThrowable(() -> new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY).create(wrongHost, null)
                .block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);
    }

}