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
import com.arangodb.next.exceptions.HostNotAvailableException;
import com.arangodb.next.exceptions.NoHostsAvailableException;
import deployments.ProxiedContainerDeployment;
import deployments.ProxiedHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.arangodb.next.communication.CommunicationTestUtils.executeRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
@Tag("resiliency")
@Testcontainers
class CommunicationResiliencyTest {

    @Container
    private final static ProxiedContainerDeployment deployment = ProxiedContainerDeployment.ofCluster(2, 2);
    private final ImmutableCommunicationConfig.Builder config;

    CommunicationResiliencyTest() {
        config = CommunicationConfig.builder()
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

        for (int j = 0; j < 100; j++) {
            executeRequest(communication, 2); // retries at most once per host
            host0.disableProxy();
            executeRequest(communication, 100);
            host1.disableProxy();

            Throwable thrown = catchThrowable(() -> executeRequest(communication));
            assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);

            host0.enableProxy();
            host1.enableProxy();
        }

        communication.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void executeWithConversation(ArangoProtocol protocol) {

        List<ProxiedHost> proxies = Arrays.asList(
                deployment.getProxiedHosts().get(0),
                deployment.getProxiedHosts().get(1)
        );

        Map<HostDescription, ProxiedHost> proxiedHosts = proxies.stream()
                .map(it -> new AbstractMap.SimpleEntry<>(it.getHostDescription(), it))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));

        ArangoCommunication communication = ArangoCommunication.create(config
                .protocol(protocol)
                .build()).block();
        assertThat(communication).isNotNull();

        for (int i = 0; i < 10; i++) {
            Conversation requiredConversation = communication.createConversation(Conversation.Level.REQUIRED);

            // update connections removing conversation host
            proxiedHosts.get(requiredConversation.getHost()).disableProxy();
            ((ArangoCommunicationImpl) communication).getConnectionPool().updateConnections(proxiedHosts.keySet()).block();

            assertThat(catchThrowable(() ->
                    CommunicationTestUtils.executeRequestAndVerifyHost(communication, requiredConversation, true)))
                    .isInstanceOf(HostNotAvailableException.class);

            Conversation preferredConversation = communication.createConversation(Conversation.Level.PREFERRED);
            CommunicationTestUtils.executeRequestAndVerifyHost(communication, preferredConversation, false);

            // update connections removing all hosts
            proxies.forEach(ProxiedHost::disableProxy);
            ((ArangoCommunicationImpl) communication).getConnectionPool().updateConnections(proxiedHosts.keySet()).block();

            assertThat(catchThrowable(() ->
                    CommunicationTestUtils.executeRequestAndVerifyHost(communication, requiredConversation, true)))
                    .isInstanceOf(HostNotAvailableException.class);

            assertThat(catchThrowable(() ->
                    CommunicationTestUtils.executeRequestAndVerifyHost(communication, preferredConversation, false)))
                    .isInstanceOf(NoHostsAvailableException.class);

            proxies.forEach(ProxiedHost::enableProxy);
            ((ArangoCommunicationImpl) communication).getConnectionPool().updateConnections(proxiedHosts.keySet()).block();
        }

        communication.close().block();
    }

}
