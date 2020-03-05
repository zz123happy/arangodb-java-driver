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

/**
 * @author Michele Rastelli
 */
public interface ArangoCommunication {

    static Mono<ArangoCommunication> create(CommunicationConfig config) {
        ConnectionConfig connectionConfig = ConnectionConfig.builder().from(config.getConnectionConfig())
                // override connection content type
                .contentType(config.getContentType())
                .build();

        ConnectionFactoryImpl connectionFactory = new ConnectionFactoryImpl(
                connectionConfig,
                config.getProtocol(),
                new ConnectionSchedulerFactory(config.getMaxThreads())
        );
        return new ArangoCommunicationImpl(config, connectionFactory).initialize();
    }

    /**
     * Initializes the communication asynchronously performing the following tasks:
     * - negotiate authentication (eg. Kerberos),
     * - acquire the host list from server,
     * - create and initialize the connections
     *
     * @return the communication ready to be used
     */
    Mono<ArangoCommunication> initialize();

    /**
     * The ArangoCommunication keeps a default conversation having level {@link Conversation.Level#REQUIRED}. It is
     * updated every time that the hostList is updated. This conversation can be used for creating sequences of requests
     * which need to hit the same coordinator.
     *
     * @return the default conversation
     * @apiNote the value is updated in a scheduled way, so subsequent requests could get a different conversation.
     */
    Conversation getDefaultConversation();

    /**
     * Send the request to a random host over a random connection
     *
     * @param request to send
     * @return response from the server
     */
    Mono<ArangoResponse> execute(ArangoRequest request);

    /**
     * Send the request to the conversation host, according to the conversation level
     *
     * @param request      to send
     * @param conversation to specify the desired host affinity
     * @return response from the server
     */
    Mono<ArangoResponse> execute(ArangoRequest request, Conversation conversation);

    /**
     * @return a new conversation
     */
    Conversation createConversation(Conversation.Level level);

    /**
     * @return a mono completing once all the connections are closed
     */
    Mono<Void> close();

}
