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

import com.arangodb.next.connection.http.HttpConnection;
import com.arangodb.next.connection.vst.VstConnection;
import com.arangodb.next.connection.vst.VstSchedulerFactory;
import reactor.core.publisher.Mono;


/**
 * @author Michele Rastelli
 */
public interface ArangoConnection {

    /**
     * @param protocol communication protocol
     * @param config   connection config
     * @return a Mono which will produce a new connection already initialized
     */
    static Mono<ArangoConnection> create(final ArangoProtocol protocol, final ConnectionConfig config) {
        switch (protocol) {
            case VST:
                return new VstConnection(config, VstSchedulerFactory.getInstance(config.getMaxThreads())).initialize();
            case HTTP:
                return new HttpConnection(config).initialize();
            default:
                throw new IllegalArgumentException(String.valueOf(protocol));
        }
    }

    /**
     * Initializes the connection asynchronously, eg. establishing the tcp connection and performing the authentication
     *
     * @return the connection ready to be used
     */
    Mono<ArangoConnection> initialize();

    /**
     * Note: the consumer is responsible to call release() on Response body
     *
     * @param request to send
     * @return response from the server
     */
    Mono<ArangoResponse> execute(final ArangoRequest request);

    Mono<Void> close();

}
