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
import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.ConnectionConfig;
import com.arangodb.next.connection.HostDescription;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.List;

/**
 * @author Michele Rastelli
 */
@Value.Immutable
public interface CommunicationConfig {
    static ImmutableCommunicationConfig.Builder builder() {
        return ImmutableCommunicationConfig.builder();
    }

    /**
     * @return ArangoDB host
     */
    List<HostDescription> getHosts();

    /**
     * @return connection configuration
     */
    @Value.Default
    default ConnectionConfig getConnectionConfig() {
        return ConnectionConfig.builder().build();
    }

    /**
     * @return network protocol
     */
    @Value.Default
    default ArangoProtocol getProtocol() {
        return ArangoProtocol.VST;
    }

    /**
     * @return whether to fetch the host list
     */
    @Value.Default
    default boolean getAcquireHostList() {
        return true;
    }

    /**
     * @return interval at which the host list will be fetched
     */
    @Value.Default
    default Duration getAcquireHostListInterval() {
        return Duration.ofMinutes(1);
    }

    /**
     * @return amount of connections that will be created for every host
     */
    @Value.Default
    default int getConnectionsPerHost() {
        return 1;
    }

    /**
     * @return max number of vst threads, used by VstConnection only
     */
    @Value.Default
    default int getMaxThreads() {
        return 4;
    }

    /**
     * @return the authenticationMethod to use
     */
    @Nullable
    AuthenticationMethod getAuthenticationMethod();

    /**
     * @return whether to negotiate the authentication (SPNEGO / Kerberos)
     */
    @Value.Default
    default boolean getNegotiateAuthentication() {
        return false;
    }

}
