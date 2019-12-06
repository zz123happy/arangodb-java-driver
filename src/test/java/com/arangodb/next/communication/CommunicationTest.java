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
import deployments.ContainerDeployment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
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
    private final static ContainerDeployment deployment = ContainerDeployment.ofCluster(2, 2);

    CommunicationTest() {
        hosts = deployment.getHosts();
        config = CommunicationConfig.builder()
                .addAllHosts(hosts)
                .authenticationMethod(deployment.getAuthentication());
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

        CommunicationTestUtils.executeRequest(communication);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void acquireHostList(ArangoProtocol protocol) {

        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .acquireHostListInterval(Duration.ofSeconds(10))
                .hosts(hosts.subList(0, 1))
                .build()).block();
        assertThat(communication).isNotNull();

        Map<HostDescription, List<ArangoConnection>> currentHosts = ((ArangoCommunicationImpl) communication).getConnectionsByHost();
        assertThat(currentHosts).hasSize(2);
        assertThat(currentHosts.keySet()).containsExactlyInAnyOrderElementsOf(hosts);

        CommunicationTestUtils.executeRequest(communication);
        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void wrongHostConnectionFailure(ArangoProtocol protocol) {
        Throwable thrown = catchThrowable(() -> ArangoCommunication.create(config
                .protocol(protocol)
                .hosts(Collections.singleton(HostDescription.of("wrongHost", 8529)))
                .build()).block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);
    }

}
