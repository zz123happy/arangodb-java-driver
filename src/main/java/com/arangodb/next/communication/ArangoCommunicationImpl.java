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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Michele Rastelli
 */
class ArangoCommunicationImpl implements ArangoCommunication {

    private static final Logger log = LoggerFactory.getLogger(ArangoCommunicationImpl.class);
    private static final Duration ACQUIRE_HOST_LIST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration UPDATE_CONNECTIONS_TIMEOUT = Duration.ofSeconds(60);

    private volatile boolean initialized = false;
    private volatile boolean updatingConnectionsSemaphore = false;
    private volatile boolean updatingHostSetSemaphore = false;

    @Nullable
    private volatile AuthenticationMethod authentication;
    private volatile Set<HostDescription> hostSet;
    private final CommunicationConfig config;
    private final ArangoConnectionFactory connectionFactory;
    private final Map<HostDescription, List<ArangoConnection>> connectionsByHost;

    ArangoCommunicationImpl(CommunicationConfig config) {
        log.debug("ArangoCommunicationImpl({})", config);

        this.config = config;
        connectionsByHost = new ConcurrentHashMap<>();
        connectionFactory = new ArangoConnectionFactory(
                config.getConnectionConfig(),
                config.getProtocol(),
                new ConnectionSchedulerFactory(config.getMaxThreads())
        );
    }

    @Override
    public synchronized Mono<ArangoCommunication> initialize() {
        log.debug("initialize()");

        if (initialized) {
            throw new IllegalStateException("Already initialized!");
        }
        initialized = true;

        return negotiateAuthentication()
                .then(Mono.defer(() -> {
                    Duration acquireHostListInterval = config.getAcquireHostListInterval();

                    if (acquireHostListInterval == Duration.ZERO) {
                        hostSet = config.getHosts();
                        return updateConnections();
                    } else {
                        return updateHostSet().doOnSuccess(v ->
                                Flux.interval(acquireHostListInterval, acquireHostListInterval)
                                        .flatMap(it -> updateHostSet())
                                        .subscribe()
                        );
                    }
                }))
                .then(Mono.just(this));
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        throw new RuntimeException("TODO");
    }

    @Override
    public Mono<Void> close() {
        List<Mono<Void>> closedConnections = connectionsByHost.values().stream()
                .flatMap(Collection::stream)
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
        return Flux.merge(closedConnections).doFinally(v -> connectionFactory.close()).then();
    }

    Map<HostDescription, List<ArangoConnection>> getConnectionsByHost() {
        return connectionsByHost;
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
    private synchronized Mono<Void> updateHostSet() {
        log.debug("updateHostSet()");

        if (updatingHostSetSemaphore) {
            return Mono.error(new IllegalStateException("Ongoing updateHostSet!"));
        }
        updatingHostSetSemaphore = true;

        // TODO

        return updateConnections()
                .doFinally(s -> updatingHostSetSemaphore = false);
    }

    /**
     * Updates the connectionsByHost map, making it consistent with the current hostSet
     *
     * @return a {@code Mono} which completes once all these conditions are met:
     * - the connectionsByHost has been updated
     * - connections related to removed hosts have been closed
     * - connections related to added hosts have been initialized
     */
    private synchronized Mono<Void> updateConnections() {
        log.debug("updateConnections()");

        if (updatingConnectionsSemaphore) {
            return Mono.error(new IllegalStateException("Ongoing updateConnections!"));
        }
        updatingConnectionsSemaphore = true;

        Set<HostDescription> currentHosts = connectionsByHost.keySet();

        List<Mono<List<ArangoConnection>>> addedHosts = hostSet.stream()
                .filter(o -> !currentHosts.contains(o))
                .map(host -> Flux.merge(createHostConnections(host)).collectList()
                        .doOnNext(hostConnections -> connectionsByHost.put(host, hostConnections)))
                .collect(Collectors.toList());

        List<Mono<List<Void>>> removedHosts = currentHosts.stream()
                .filter(o -> !hostSet.contains(o))
                .map(host -> Flux.merge(closeHostConnections(connectionsByHost.remove(host))).collectList())
                .collect(Collectors.toList());

        return Flux.merge(Flux.merge(addedHosts), Flux.merge(removedHosts))
                .timeout(UPDATE_CONNECTIONS_TIMEOUT)
                .doFinally(s -> updatingConnectionsSemaphore = false)
                .then();
    }

    private List<Mono<ArangoConnection>> createHostConnections(HostDescription host) {
        log.debug("createHostConnections({})", host);

        return IntStream.range(0, config.getConnectionsPerHost())
                .mapToObj(i -> connectionFactory.create(host, authentication))
                .collect(Collectors.toList());
    }

    private List<Mono<Void>> closeHostConnections(List<ArangoConnection> connections) {
        log.debug("closeHostConnections({})", connections);

        return connections.stream()
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
    }

}
