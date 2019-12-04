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
import com.arangodb.velocypack.VPack;
import com.arangodb.velocypack.VPackSlice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import java.time.Duration;
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

    // TODO: mv to CommunicationConfig
    private static final int OPERATIONS_RETRIES = 10;
    private static final Duration OPERATIONS_TIMEOUT = Duration.ofSeconds(10);

    private volatile boolean initialized = false;
    private volatile boolean updatingConnectionsSemaphore = false;
    private volatile boolean updatingHostListSemaphore = false;

    @Nullable
    private volatile AuthenticationMethod authentication;

    @Nullable
    private volatile Disposable scheduledUpdateHostListSubscription;
    private volatile List<HostDescription> hostList;
    private final CommunicationConfig config;
    private final ArangoConnectionFactory connectionFactory;
    private final Map<HostDescription, List<ArangoConnection>> connectionsByHost;

    private static final ArangoRequest acquireHostListRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/cluster/endpoints")
            .requestType(ArangoRequest.RequestType.GET)
            .build();

    ArangoCommunicationImpl(CommunicationConfig config) {
        log.debug("ArangoCommunicationImpl({})", config);

        this.config = config;
        hostList = config.getHosts();
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
                .then(Mono.defer(this::updateConnections))
                .then(Mono.defer(this::scheduleUpdateHostList))
                .then(Mono.just(this));
    }

    @Override
    public Mono<ArangoResponse> execute(ArangoRequest request) {
        return Mono.defer(() -> executeOnRandomHost(request))
                .retry(OPERATIONS_RETRIES)
                .timeout(OPERATIONS_TIMEOUT);
    }

    private Mono<ArangoResponse> executeOnRandomHost(ArangoRequest request) {
        HostDescription host = getRandomItem(connectionsByHost.keySet());
        log.debug("executeOnRandomHost: picked host {}", host);
        ArangoConnection connection = getRandomItem(connectionsByHost.get(host));
        return connection.execute(request);
    }

    @Override
    public Mono<Void> close() {
        Optional.ofNullable(scheduledUpdateHostListSubscription).ifPresent(Disposable::dispose);
        List<Mono<Void>> closedConnections = connectionsByHost.values().stream()
                .flatMap(Collection::stream)
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
        return Flux.merge(closedConnections).doFinally(v -> connectionFactory.close()).then();
    }

    private static <T> T getRandomItem(final Collection<T> collection) {
        int index = ThreadLocalRandom.current().nextInt(collection.size());
        Iterator<T> iter = collection.iterator();
        for (int i = 0; i < index; i++) {
            iter.next();
        }
        return iter.next();
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
    private synchronized Mono<Void> updateHostList() {
        log.debug("updateHostList()");

        if (updatingHostListSemaphore) {
            return Mono.error(new IllegalStateException("Ongoing updateHostList!"));
        }
        updatingHostListSemaphore = true;

        return execute(acquireHostListRequest)
                .map(this::parseAcquireHostListResponse)
                .doOnNext(it -> hostList = it)
                .then(Mono.defer(this::updateConnections))
                .doFinally(s -> updatingHostListSemaphore = false);
    }

    private List<HostDescription> parseAcquireHostListResponse(ArangoResponse response) {
        // TODO: handle exceptions           response.getResponseCode() != 200
        VPackSlice responseBodySlice = new VPackSlice(IOUtils.getByteArray(response.getBody()));
        VPackSlice field = responseBodySlice.get("endpoints");
        final Collection<Map<String, String>> entity = new VPack.Builder().build().deserialize(field, Collection.class);
        return entity.stream()
                .map(it -> it.get("endpoint"))
                // TODO: support ipv6 addresses
                .map(it -> it.replaceFirst(".*://", "").split(":"))
                .map(it -> HostDescription.of(it[0], Integer.parseInt(it[1])))
                .collect(Collectors.toList());
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

        List<Mono<List<ArangoConnection>>> addedHosts = hostList.stream()
                .filter(o -> !currentHosts.contains(o))
                .map(host -> Flux.merge(createHostConnections(host)).collectList()
                        .doOnNext(hostConnections -> connectionsByHost.put(host, hostConnections)))
                .collect(Collectors.toList());

        List<Mono<List<Void>>> removedHosts = currentHosts.stream()
                .filter(o -> !hostList.contains(o))
                .map(host -> Flux.merge(closeHostConnections(connectionsByHost.remove(host))).collectList())
                .collect(Collectors.toList());

        return Flux.merge(Flux.merge(addedHosts), Flux.merge(removedHosts))
                .timeout(OPERATIONS_TIMEOUT)
                .doFinally(s -> updatingConnectionsSemaphore = false)
                .then();
    }

    private Mono<Void> scheduleUpdateHostList() {
        Duration acquireHostListInterval = config.getAcquireHostListInterval();
        if (acquireHostListInterval != Duration.ZERO) {
            scheduledUpdateHostListSubscription = Flux.interval(acquireHostListInterval)
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
                        .retry(OPERATIONS_RETRIES)
                )
                .collect(Collectors.toList());
    }

    private List<Mono<Void>> closeHostConnections(List<ArangoConnection> connections) {
        log.debug("closeHostConnections({})", connections);

        return connections.stream()
                .map(ArangoConnection::close)
                .collect(Collectors.toList());
    }

}
