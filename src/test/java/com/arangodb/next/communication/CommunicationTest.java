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
import com.arangodb.velocypack.VPackSlice;
import deployments.ProxiedContainerDeployment;
import deployments.ProxiedHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class CommunicationTest {

    private final ImmutableCommunicationConfig.Builder config;
    private final List<HostDescription> hosts;

    @Container
    private final static ProxiedContainerDeployment deployment = ProxiedContainerDeployment.ofCluster(2, 2);

    CommunicationTest() {
        hosts = deployment.getHosts();
        config = CommunicationConfig.builder()
                .protocol(ArangoProtocol.VST)
                .addAllHosts(hosts)
                .authenticationMethod(deployment.getAuthentication())
                .connectionConfig(ConnectionConfig.builder()
                        .build());
    }

    @BeforeEach
    void restore() {
        deployment.getProxiedHosts().forEach(it -> {
            it.enableProxy();
            it.getProxy().setConnectionCut(false);
        });
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void create(ArangoProtocol protocol) {
        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .build()).block();
        assertThat(communication).isNotNull();

        Map<HostDescription, List<ArangoConnection>> connectionsByHost =
                ((ArangoCommunicationImpl) communication).getConnectionsByHost();
        HostDescription[] expectedKeys = hosts.toArray(new HostDescription[0]);

        assertThat(connectionsByHost)
                .containsKeys(expectedKeys)
                .containsOnlyKeys(expectedKeys);

        // check if every connection is connected
        connectionsByHost.forEach((key, value) ->
                value.forEach(ConnectionTestUtils::performRequest));

        communication.execute(ConnectionTestUtils.versionRequest);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void acquireHostList(ArangoProtocol protocol) throws InterruptedException {

        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .acquireHostListInterval(Duration.ofSeconds(10))
                .hosts(hosts.subList(0, 1))
                .build()).block();
        assertThat(communication).isNotNull();

        Thread.sleep(500);

        assertThat(((ArangoCommunicationImpl) communication).getConnectionsByHost()).hasSize(2);
        communication.close().block();
    }

    @Test
    void wrongHostVstConnectionFailure() {
        Throwable thrown = catchThrowable(() -> ArangoCommunication.create(config
                .protocol(ArangoProtocol.VST)
                .hosts(Collections.singleton(HostDescription.of("wrongHost", 8529)))
                .build()).block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(UnknownHostException.class);
    }

    @Test
    void wrongHostHttpRequestFailure() {
        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(ArangoProtocol.HTTP)
                .hosts(Collections.singleton(HostDescription.of("wrongHost", 8529)))
                .build()).block();
        assertThat(communication).isNotNull();
        Throwable thrown = catchThrowable(() -> communication.execute(ConnectionTestUtils.versionRequest).block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(UnknownHostException.class);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void retry(ArangoProtocol protocol) {
        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .build()).block();
        assertThat(communication).isNotNull();

        ProxiedHost host0 = deployment.getProxiedHosts().get(0);
        ProxiedHost host1 = deployment.getProxiedHosts().get(1);

        for (int i = 0; i < 10; i++) {
            executeRequest(communication);
        }

        host0.disableProxy();

        for (int i = 0; i < 10; i++) {
            executeRequest(communication);
        }

        host1.disableProxy();

        Throwable thrown = catchThrowable(() -> executeRequest(communication));
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);

        host0.enableProxy();

        for (int i = 0; i < 10; i++) {
            executeRequest(communication);
        }

        communication.close().block();
    }

    private static void executeRequest(ArangoCommunication communication) {
        ArangoResponse response = communication.execute(ConnectionTestUtils.versionRequest).block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(IOUtils.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");

        response.getBody().release();
    }

}
