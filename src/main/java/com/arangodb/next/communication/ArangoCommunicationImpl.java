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
import com.arangodb.next.entity.ClusterEndpoints;
import com.arangodb.next.entity.ErrorEntity;
import com.arangodb.next.entity.codec.ArangoDeserializer;
import com.arangodb.next.exceptions.ArangoServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * @author Michele Rastelli
 */
class ArangoCommunicationImpl implements ArangoCommunication {

    private static final Logger log = LoggerFactory.getLogger(ArangoCommunicationImpl.class);

    private final CommunicationConfig config;
    private final ArangoDeserializer deserializer;
    private final ConnectionFactory connectionFactory;

    // connection pool used to acquireHostList
    private volatile ConnectionPool contactConnectionPool;
    // connection pool used for all other operations
    private volatile ConnectionPool connectionPool;

    private volatile boolean initialized = false;
    private volatile boolean updatingHostListSemaphore = false;
    @Nullable
    private volatile AuthenticationMethod authentication;
    @Nullable
    private volatile Disposable scheduledUpdateHostListSubscription;

    private static final ArangoRequest acquireHostListRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/cluster/endpoints")
            .requestType(ArangoRequest.RequestType.GET)
            .build();

    ArangoCommunicationImpl(CommunicationConfig config, ConnectionFactory connectionFactory) {
        log.debug("ArangoCommunicationImpl({}, {})", config, connectionFactory);

        this.config = config;
        this.connectionFactory = connectionFactory;
        deserializer = ArangoDeserializer.of(config.getContentType());
    }

    @Override
    public synchronized Mono<ArangoCommunication> initialize() {
        log.debug("initialize()");

        if (initialized) {
            throw new IllegalStateException("Already initialized!");
        }
        initialized = true;

        return negotiateAuthentication()
                .doOnSuccess(__ -> {
                    CommunicationConfig contactPoolConfig = CommunicationConfig.builder().from(config)
                            .topology(ArangoTopology.SINGLE_SERVER)
                            .connectionsPerHost(1)
                            .build();
                    contactConnectionPool = ConnectionPool.create(contactPoolConfig, authentication, connectionFactory);
                    connectionPool = ConnectionPool.create(config, authentication, connectionFactory);
                })
                .then(Mono.defer(() -> {
                    if (config.getAcquireHostList()) {
                        return contactConnectionPool.updateConnections(config.getHosts());
                    } else {
                        return connectionPool.updateConnections(config.getHosts());
                    }
                }))
                .then(Mono.defer(this::scheduleUpdateHostList))
                .then(Mono.just(this));
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        log.debug("execute({})", request);
        return execute(request, connectionPool);
    }

    private Mono<ArangoResponse> execute(ArangoRequest request, ConnectionPool cp) {
        log.debug("execute({}, {})", request, cp);
        return Mono.defer(() -> cp.executeOnRandomHost(request)).timeout(config.getTimeout());
    }

    @Override
    public Mono<Void> close() {
        log.debug("close()");
        Optional.ofNullable(scheduledUpdateHostListSubscription).ifPresent(Disposable::dispose);
        return connectionPool.close().then();
    }

    ConnectionPool getConnectionPool() {
        return connectionPool;
    }

    /**
     * hook to perform kerberos authentication negotiation -- for future use
     *
     * <p>
     * Implementation should overwrite this::authenticationMethod with an AuthenticationMethod that can be used
     * to connect to the db
     * </p>
     *
     * @return a {@code Mono} which completes once this::authenticationMethod has been correctly set
     */
    private synchronized Mono<Void> negotiateAuthentication() {
        log.debug("negotiateAuthentication()");

        if (config.getNegotiateAuthentication()) {
            throw new RuntimeException("Authentication Negotiation is not yet supported!");
        } else {
            authentication = config.getAuthenticationMethod();
            return Mono.empty();
        }
    }

    /**
     * Fetches from the server the host set and update accordingly the connections
     *
     * @return a {@code Mono} which completes once all these conditions are met:
     * - the connectionsByHost has been updated
     * - connections related to removed hosts have been closed
     * - connections related to added hosts have been initialized
     */
    synchronized Mono<Void> updateHostList() {
        log.debug("updateHostList()");

        if (updatingHostListSemaphore) {
            return Mono.error(new IllegalStateException("Ongoing updateHostList!"));
        }
        updatingHostListSemaphore = true;

        return execute(acquireHostListRequest, contactConnectionPool)
                .map(this::parseAcquireHostListResponse)
                .doOnError(e -> log.warn("Error acquiring hostList, retrying...", e))
                .retry(config.getRetries())
                .doOnNext(acquiredHostList -> log.debug("Acquired hosts: {}", acquiredHostList))
                .doOnError(e -> log.warn("Error acquiring hostList:", e))
                .flatMap(hostList -> connectionPool.updateConnections(hostList))
                .timeout(config.getTimeout())
                .doFinally(s -> updatingHostListSemaphore = false);
    }

    private List<HostDescription> parseAcquireHostListResponse(ArangoResponse response) {
        log.debug("parseAcquireHostListResponse({})", response);
        if (response.getResponseCode() != 200) {
            throw ArangoServerException.builder()
                    .responseCode(response.getResponseCode())
                    .entity(deserializer.deserialize(response.getBody(), ErrorEntity.class))
                    .build();
        }
        return deserializer.deserialize(response.getBody(), ClusterEndpoints.class)
                .getHostDescriptions();
    }

    private Mono<Void> scheduleUpdateHostList() {
        if (config.getAcquireHostList()) {
            scheduledUpdateHostListSubscription = Flux.interval(config.getAcquireHostListInterval())
                    .flatMap(it -> updateHostList())
                    .subscribe();
            return updateHostList();
        } else {
            return Mono.empty();
        }
    }

}
