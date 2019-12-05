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

import com.arangodb.next.connection.ArangoProtocol;
import com.arangodb.next.connection.ConnectionConfig;
import com.arangodb.next.connection.HostDescription;
import deployments.ProxiedContainerDeployment;
import deployments.ProxiedHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
import java.util.List;

import static com.arangodb.next.communication.CommunicationTestUtils.executeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class CommunicationResiliencyTest {

    private final ImmutableCommunicationConfig.Builder config;
    private final List<HostDescription> hosts;

    @Container
    private final static ProxiedContainerDeployment deployment = ProxiedContainerDeployment.ofCluster(2, 2);

    CommunicationResiliencyTest() {
        hosts = deployment.getHosts();
        config = CommunicationConfig.builder()
                .addAllHosts(hosts)
                .authenticationMethod(deployment.getAuthentication())
                // use proxied hostDescriptions
                .acquireHostList(false);
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
    void retry(ArangoProtocol protocol) {
        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .connectionConfig(
                        ConnectionConfig.builder()
                                .timeout(500)
                                .build())
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

}
