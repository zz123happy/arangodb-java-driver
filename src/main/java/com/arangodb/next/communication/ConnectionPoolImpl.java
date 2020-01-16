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
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Michele Rastelli
 */
class ConnectionPoolImpl implements ConnectionPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionPoolImpl.class);

    private final Map<HostDescription, List<ArangoConnection>> connectionsByHost;
    private final CommunicationConfig config;
    private final ConnectionFactory connectionFactory;
    private final AuthenticationMethod authentication;
    private final Semaphore updatingConnectionsSemaphore;

    protected static <T> T getRandomItem(final Collection<T> collection) {
        int index = ThreadLocalRandom.current().nextInt(collection.size());
        Iterator<T> iterator = collection.iterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator.next();
    }

    ConnectionPoolImpl(
            final CommunicationConfig config,
            final AuthenticationMethod authentication,
            final ConnectionFactory connectionFactory
    ) {
        LOGGER.debug("ArangoCommunicationImpl({}, {}, {})", config, authentication, connectionFactory);

        this.config = config;
        this.authentication = authentication;
        this.connectionFactory = connectionFactory;
        updatingConnectionsSemaphore = new Semaphore(1);
        connectionsByHost = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<ArangoResponse> executeOnRandomHost(final ArangoRequest request) {
        if (connectionsByHost.isEmpty()) {
            return Mono.error(new IOException("No open connections!"));
        }
        HostDescription host = getRandomItem(connectionsByHost.keySet());
        LOGGER.debug("executeOnRandomHost: picked host {}", host);
        ArangoConnection connection = getRandomItem(connectionsByHost.get(host));
        return connection.execute(request);
    }

    @Override
    public Mono<Void> close() {
        LOGGER.debug("close()");
        List<Mono<Void>> closedConnections = connectionsByHost.values().stream()
                .flatMap(Collection::stream)
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
        return Flux.merge(closedConnections).doFinally(v -> connectionFactory.close()).then();
    }

    protected Map<HostDescription, List<ArangoConnection>> getConnectionsByHost() {
        return connectionsByHost;
    }

    @Override
    public Mono<Void> updateConnections(final List<HostDescription> hostList) {
        LOGGER.debug("updateConnections()");

        if (!updatingConnectionsSemaphore.tryAcquire()) {
            return Mono.error(new IllegalStateException("Ongoing updateConnections!"));
        }

        Set<HostDescription> currentHosts = connectionsByHost.keySet();

        List<Mono<Void>> addedHosts = hostList.stream()
                .filter(o -> !currentHosts.contains(o))
                .peek(host -> LOGGER.debug("adding host: {}", host))
                .map(host -> Flux
                        .merge(createHostConnections(host))
                        .collectList()
                        .flatMap(hostConnections -> {
                            if (hostConnections.isEmpty()) {
                                LOGGER.warn("not able to connect to host [{}], skipped adding host!", host);
                                return removeHost(host);
                            } else {
                                connectionsByHost.put(host, hostConnections);
                                LOGGER.debug("added host: {}", host);
                                return Mono.empty();
                            }
                        })
                )
                .collect(Collectors.toList());

        List<Mono<Void>> removedHosts = currentHosts.stream()
                .filter(o -> !hostList.contains(o))
                .map(this::removeHost)
                .collect(Collectors.toList());

        return Flux.merge(Flux.merge(addedHosts), Flux.merge(removedHosts))
                .then(Mono.defer(this::removeDisconnectedHosts))
                .timeout(config.getTimeout())
                .then(Mono.defer(() -> {
                    if (connectionsByHost.isEmpty()) {
                        return Mono.<Void>error(Exceptions.bubble(new IOException("Could not create any connection.")));
                    } else {
                        return Mono.empty();
                    }
                }))

                // here we cannot use Flux::doFinally since the signal is propagated downstream before the callback is
                // executed and this is a problem if a chained task re-invoke this method, eg. during {@link this#initialize}
                .doOnTerminate(() -> {
                    LOGGER.debug("updateConnections complete: {}", connectionsByHost.keySet());
                    updatingConnectionsSemaphore.release();
                })
                .doOnCancel(updatingConnectionsSemaphore::release);
    }

    /**
     * removes all the hosts that are disconnected
     *
     * @return a Mono completing when the hosts have been removed
     */
    private Mono<Void> removeDisconnectedHosts() {
        List<Mono<HostDescription>> hostsToRemove = connectionsByHost.entrySet().stream()
                .map(hostConnections -> checkAllDisconnected(hostConnections.getValue())
                        .flatMap(hostDisconnected -> {
                            if (hostDisconnected) {
                                return Mono.just(hostConnections.getKey());
                            } else {
                                return Mono.empty();
                            }
                        }))
                .collect(Collectors.toList());

        return Flux.merge(hostsToRemove)
                .flatMap(this::removeHost)
                .then();
    }

    private Mono<Void> removeHost(final HostDescription host) {
        LOGGER.debug("removing host: {}", host);
        return Optional.ofNullable(connectionsByHost.remove(host))
                .map(this::closeHostConnections)
                .orElse(Mono.empty());
    }

    private List<Mono<ArangoConnection>> createHostConnections(final HostDescription host) {
        LOGGER.debug("createHostConnections({})", host);

        return IntStream.range(0, config.getConnectionsPerHost())
                .mapToObj(i -> Mono.defer(() -> connectionFactory.create(host, authentication))
                        .retry(config.getRetries())
                        .doOnNext(it -> LOGGER.debug("created connection to host: {}", host))
                        .doOnError(e -> LOGGER.warn("Error creating connection:", e))
                        .onErrorResume(e -> Mono.empty()) // skips the failing connections
                )
                .collect(Collectors.toList());
    }

    private Mono<Void> closeHostConnections(final List<ArangoConnection> connections) {
        LOGGER.debug("closeHostConnections({})", connections);

        return Flux.merge(
                connections.stream()
                        .map(ArangoConnection::close)
                        .collect(Collectors.toList())
        ).then();
    }

    /**
     * @param connections to check
     * @return Mono<True> if all the provided connections are disconnected
     */
    private Mono<Boolean> checkAllDisconnected(final List<ArangoConnection> connections) {
        return Flux
                .merge(
                        connections.stream()
                                .map(ArangoConnection::isConnected)
                                .collect(Collectors.toList())
                )
                .collectList()
                .map(areConnected -> areConnected.stream().noneMatch(i -> i));
    }

}
