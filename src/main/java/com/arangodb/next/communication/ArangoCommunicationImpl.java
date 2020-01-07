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
import com.arangodb.next.entity.codec.ArangoDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author Michele Rastelli
 */
class ArangoCommunicationImpl implements ArangoCommunication {

    private static final Logger log = LoggerFactory.getLogger(ArangoCommunicationImpl.class);

    private final CommunicationConfig config;
    private final ConnectionFactory connectionFactory;
    private final Map<HostDescription, List<ArangoConnection>> connectionsByHost;
    private final ArangoDeserializer deserializer;

    private volatile boolean initialized = false;
    private volatile boolean updatingConnectionsSemaphore = false;
    private volatile boolean updatingHostListSemaphore = false;
    @Nullable
    private volatile AuthenticationMethod authentication;
    @Nullable
    private volatile Disposable scheduledUpdateHostListSubscription;
    private volatile List<HostDescription> hostList = Collections.emptyList();

    private static final ArangoRequest acquireHostListRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/cluster/endpoints")
            .requestType(ArangoRequest.RequestType.GET)
            .build();

    ArangoCommunicationImpl(CommunicationConfig config, ConnectionFactory connectionFactory) {
        log.debug("ArangoCommunicationImpl({})", config);

        this.config = config;
        this.connectionFactory = connectionFactory;
        setHostList(config.getHosts());
        connectionsByHost = new ConcurrentHashMap<>();
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
                .then(Mono.defer(this::updateConnections))
                .then(Mono.defer(this::scheduleUpdateHostList))
                .then(Mono.just(this));
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        log.debug("execute({})", request);
        return Mono.defer(() -> executeOnRandomHost(request))
                .timeout(config.getTimeout());
    }

    private Mono<ArangoResponse> executeOnRandomHost(ArangoRequest request) {
        if (connectionsByHost.isEmpty()) {
            return Mono.error(new IOException("No open connections!"));
        }
        HostDescription host = getRandomItem(connectionsByHost.keySet());
        log.debug("executeOnRandomHost: picked host {}", host);
        ArangoConnection connection = getRandomItem(connectionsByHost.get(host));
        return connection.execute(request);
    }

    @Override
    public Mono<Void> close() {
        log.debug("close()");
        Optional.ofNullable(scheduledUpdateHostListSubscription)
                .ifPresent(Disposable::dispose);
        List<Mono<Void>> closedConnections = connectionsByHost.values().stream()
                .flatMap(Collection::stream)
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
        return Flux.merge(closedConnections).doFinally(v -> connectionFactory.close()).then();
    }

    private static <T> T getRandomItem(final Collection<T> collection) {
        int index = ThreadLocalRandom.current().nextInt(collection.size());
        Iterator<T> iterator = collection.iterator();
        for (int i = 0; i < index; i++) {
            iterator.next();
        }
        return iterator.next();
    }

    /**
     * @return a copy of connectionsByHost
     */
    Map<HostDescription, List<ArangoConnection>> getConnectionsByHost() {
        return connectionsByHost.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), new LinkedList<>(e.getValue())))
                .collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
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

        return execute(acquireHostListRequest)
                .map(this::parseAcquireHostListResponse)
                .doOnError(e -> log.warn("Error acquiring hostList, retrying...", e))
                .retry(config.getRetries())
                .doOnNext(acquiredHostList -> log.debug("Acquired hosts: {}", acquiredHostList))
                .doOnError(e -> log.warn("Error acquiring hostList:", e))
                .onErrorReturn(config.getHosts())   // use the hosts from config
                .doOnNext(this::setHostList)
                .then(Mono.defer(this::updateConnections))
                .timeout(config.getTimeout())
                .doFinally(s -> updatingHostListSemaphore = false);
    }

    private List<HostDescription> parseAcquireHostListResponse(ArangoResponse response) {
        log.debug("parseAcquireHostListResponse({})", response);
        // TODO: handle exceptions           response.getResponseCode() != 200
        byte[] responseBuffer = IOUtils.getByteArray(response.getBody());
        response.getBody().release();
        return deserializer.deserialize(responseBuffer, ClusterEndpoints.class)
                .getHostDescriptions();
    }

    /**
     * Updates the connectionsByHost map, making it consistent with the current hostList
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

        List<Mono<Void>> addedHosts = hostList.stream()
                .filter(o -> !currentHosts.contains(o))
                .peek(host -> log.debug("adding host: {}", host))
                .map(host -> Flux
                        .merge(createHostConnections(host))
                        .collectList()
                        .flatMap(hostConnections -> {
                            if (hostConnections.isEmpty()) {
                                log.warn("not able to connect to host [{}], skipped adding host!", host);
                                return removeHost(host);
                            } else {
                                connectionsByHost.put(host, hostConnections);
                                log.debug("added host: {}", host);
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
                    log.debug("updateConnections complete: {}", connectionsByHost.keySet());
                    updatingConnectionsSemaphore = false;
                })
                .doOnCancel(() -> updatingConnectionsSemaphore = false);
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

    private Mono<Void> removeHost(HostDescription host) {
        log.debug("removing host: {}", host);
        return Optional.ofNullable(connectionsByHost.remove(host))
                .map(this::closeHostConnections)
                .orElse(Mono.empty());
    }

    /**
     * @param connections to check
     * @return Mono<True> if all the provided connections are disconnected
     */
    private Mono<Boolean> checkAllDisconnected(List<ArangoConnection> connections) {
        return Flux
                .merge(
                        connections.stream()
                                .map(ArangoConnection::isConnected)
                                .collect(Collectors.toList())
                )
                .collectList()
                .map(areConnected -> areConnected.stream().noneMatch(i -> i));
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

    private List<Mono<ArangoConnection>> createHostConnections(HostDescription host) {
        log.debug("createHostConnections({})", host);

        return IntStream.range(0, config.getConnectionsPerHost())
                .mapToObj(i -> Mono.defer(() -> connectionFactory.create(host, authentication))
                        .retry(config.getRetries())
                        .doOnNext(it -> log.debug("created connection to host: {}", host))
                        .doOnError(e -> log.warn("Error creating connection:", e))
                        .onErrorResume(e -> Mono.empty()) // skips the failing connections
                )
                .collect(Collectors.toList());
    }

    private Mono<Void> closeHostConnections(List<ArangoConnection> connections) {
        log.debug("closeHostConnections({})", connections);

        return Flux.merge(
                connections.stream()
                        .map(ArangoConnection::close)
                        .collect(Collectors.toList())
        ).then();
    }

    private void setHostList(List<HostDescription> hostList) {
        log.debug("setHostList({})", hostList);
        this.hostList = hostList;
    }

}
