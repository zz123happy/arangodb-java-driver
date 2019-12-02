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
import deployments.ProxiedContainerDeployment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static com.arangodb.next.connection.ConnectionTestUtils.DEFAULT_SCHEDULER_FACTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class ReconnectionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReconnectionTest.class);

    private final AuthenticationMethod authentication = AuthenticationMethod.ofBasic("root", "test");
    private final ImmutableConnectionConfig.Builder config;
    private final ArangoRequest getRequest;

    @Container
    private static final ProxiedContainerDeployment container = ProxiedContainerDeployment.ofSingleServer();

    ReconnectionTest() {
        config = ConnectionConfig.builder();

        getRequest = ArangoRequest.builder()
                .database("_system")
                .path("/_api/version")
                .requestType(ArangoRequest.RequestType.GET)
                .putQueryParam("details", "true")
                .build();
    }

    @BeforeEach
    void restore() {
        container.enableProxy();
        container.getProxy().setConnectionCut(false);
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void requestTimeout(ArangoProtocol protocol) {
        HostDescription host = container.getHosts().get(0);
        ConnectionConfig testConfig = config.timeout(1000).build();
        ArangoConnection connection = new ArangoConnectionFactory(testConfig, protocol, DEFAULT_SCHEDULER_FACTORY)
                .create(host, authentication).block();
        assertThat(connection).isNotNull();

        performRequest(connection);

        container.getProxy().setConnectionCut(true);
        Throwable thrown = catchThrowable(() -> performRequest(connection));
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(TimeoutException.class);

        container.getProxy().setConnectionCut(false);
        performRequest(connection);

        connection.close().block();
    }


    @Test
    void VstConnectionTimeout() {
        HostDescription host = container.getHosts().get(0);
        ConnectionConfig testConfig = config.timeout(1000).build();
        container.getProxy().setConnectionCut(true);
        Throwable thrown = catchThrowable(() -> new ArangoConnectionFactory(testConfig, ArangoProtocol.VST, DEFAULT_SCHEDULER_FACTORY).create(host, authentication).block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(TimeoutException.class);
    }


    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void reconnect(ArangoProtocol protocol) {
        HostDescription host = container.getHosts().get(0);
        ConnectionConfig testConfig = config.build();
        ArangoConnection connection = new ArangoConnectionFactory(testConfig, protocol, DEFAULT_SCHEDULER_FACTORY).create(host, authentication).block();
        assertThat(connection).isNotNull();

        for (int i = 0; i < 100; i++) {
            performRequest(connection);
            container.disableProxy();
            Throwable thrown = catchThrowable(() -> performRequest(connection));
            assertThat(Exceptions.unwrap(thrown)).isInstanceOfAny(IOException.class, TimeoutException.class);
            container.enableProxy();
            performRequest(connection);
        }

        connection.close().block();
    }


    private void performRequest(ArangoConnection connection) {
        ArangoResponse response = connection.execute(getRequest).block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");

        response.getBody().release();
    }

}