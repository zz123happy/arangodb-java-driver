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
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.arangodb.next.communication.CommunicationTestUtils.executeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class ActiveFailoverResiliencyTest {

    private final ImmutableCommunicationConfig.Builder config;

    @Container
    private final static ProxiedContainerDeployment deployment = ProxiedContainerDeployment.ofActiveFailover(3);

    ActiveFailoverResiliencyTest() {
        config = CommunicationConfig.builder()
                .topology(ArangoTopology.ACTIVE_FAILOVER)
                .addAllHosts(deployment.getHosts())
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
        CommunicationConfig testConfig = config
                .protocol(protocol)
                .build();
        ArangoCommunication communication = ArangoCommunication.create(testConfig).block();
        assertThat(communication).isNotNull();

        List<ProxiedHost> proxies = deployment.getProxiedHosts();

        Map<HostDescription, ProxiedHost> proxiedHosts = proxies.stream()
                .map(it -> new AbstractMap.SimpleEntry<>(it.getHostDescription(), it))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        HostDescription leader = ((ActiveFailoverConnectionPool) ((ArangoCommunicationImpl) communication)
                .getConnectionPool()).getLeader();

        ProxiedHost leaderProxy = proxiedHosts.get(leader);

        for (int j = 0; j < 10; j++) {
            executeRequest(communication, 2);
            leaderProxy.disableProxy();
            assertThat(Exceptions.unwrap(catchThrowable(() -> executeRequest(communication, 2))))
                    .isInstanceOf(IOException.class);
            leaderProxy.enableProxy();
        }

        communication.close().block();
    }

}
