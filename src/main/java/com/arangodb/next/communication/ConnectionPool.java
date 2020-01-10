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
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author Michele Rastelli
 */
interface ConnectionPool {

    static ConnectionPool create(
            final CommunicationConfig config,
            final AuthenticationMethod authentication,
            final ConnectionFactory connectionFactory) {

        switch (config.topology()) {
            case ACTIVE_FAILOVER:
                return new LeaderFollowerConnectionPool(config, authentication, connectionFactory);
            case SINGLE_SERVER:
            case CLUSTER:
                return new ConnectionPoolImpl(config, authentication, connectionFactory);
            default:
                throw new IllegalArgumentException();
        }

    }

    /**
     * @return a mono completing once all the connections are closed
     */
    Mono<Void> close();

    /**
     * Executes the request on a random host
     *
     * @param request to be executed
     * @return db response
     */
    Mono<ArangoResponse> executeOnRandomHost(ArangoRequest request);

    /**
     * Updates the connectionsByHost map, making it consistent with the current hostList
     *
     * @return a {@code Mono} which completes once all these conditions are met:
     * - the connectionsByHost has been updated
     * - connections related to removed hosts have been closed
     * - connections related to added hosts have been initialized
     */
    Mono<Void> updateConnections(final List<HostDescription> hostList);

}