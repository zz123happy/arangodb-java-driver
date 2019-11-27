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


import com.arangodb.next.connection.ConnectionConfig;
import com.arangodb.next.connection.HostDescription;
import org.immutables.value.Value;

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
     * @return amount of connections that will be created for every host
     */
    default int getConnectionsPerHost() {
        return 1;
    }

    /**
     * @return connection configuration
     */
    ConnectionConfig getConnectionConfig();

    /**
     * @return max number of vst threads, used by VstConnection only
     */
    @Value.Default
    default int getMaxThreads() {
        return 4;
    }
}
