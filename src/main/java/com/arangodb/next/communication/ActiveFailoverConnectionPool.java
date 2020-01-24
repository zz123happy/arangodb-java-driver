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
import com.arangodb.next.exceptions.LeaderNotAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * @author Michele Rastelli
 */
final class ActiveFailoverConnectionPool extends ConnectionPoolImpl {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveFailoverConnectionPool.class);

    private final Semaphore findLeaderSemaphore;
    private volatile HostDescription leader;

    ActiveFailoverConnectionPool(
            final CommunicationConfig config,
            final AuthenticationMethod authentication,
            final ConnectionFactory connectionFactory
    ) {
        super(config, authentication, connectionFactory);
        findLeaderSemaphore = new Semaphore(1);
    }

    private static boolean isReadRequest(final ArangoRequest request) {
        return ArangoRequest.RequestType.GET.equals(request.getRequestType())
                || ArangoRequest.RequestType.HEAD.equals(request.getRequestType());
    }

    @Override
    public Mono<Void> updateConnections(final Set<HostDescription> hostList) {
        return super.updateConnections(hostList).then(Mono.defer(this::findLeader));
    }

    @Override
    public Mono<ArangoResponse> execute(final ArangoRequest request) {
        LOGGER.debug("execute({})", request);

        if (getConfig().getDirtyReads() && isReadRequest(request)) {
            // executes on random host
            return super.execute(
                    ArangoRequest.builder()
                            .from(request)
                            .putHeaderParam("X-Arango-Allow-Dirty-Read", "true")
                            .build()
            );
        } else {
            return executeOnLeader(request);
        }

    }

    private Mono<ArangoResponse> executeOnLeader(final ArangoRequest request) {
        return execute(request, leader)
                .doOnNext(response -> {
                    if (response.getResponseCode() == 503) {
                        findLeader().subscribe();
                    }
                })
                .doOnError(e -> findLeader().subscribe());
    }

    HostDescription getLeader() {
        return leader;
    }

    /**
     * Sets {@link this#leader} finding the leader among the existing {@link this#getConnectionsByHost()}.
     *
     * @return a mono completing when done
     */
    private Mono<Void> findLeader() {
        if (!findLeaderSemaphore.tryAcquire()) {
            return Mono.empty();
        }

        return Flux.fromIterable(getConnectionsByHost().entrySet())
                .flatMap(e -> e.getValue().get(0).requestUser()
                        .filter(response -> response.getResponseCode() == 200)
                        .doOnNext(response -> {
                            if (!e.getKey().equals(leader)) {
                                leader = e.getKey();
                                LOGGER.info("findLeader(): found new leader {}", leader);
                            }
                        })
                )
                .onErrorContinue((throwable, o) -> LOGGER.warn("findLeader(): error contacting {}", o, throwable))
                .switchIfEmpty(Mono.error(LeaderNotAvailableException.builder().build()))
                .doOnError(err -> LOGGER.warn("findLeader(): ", err))
                .doFinally(type -> findLeaderSemaphore.release())
                .then();
    }

    @Override
    public Conversation createConversation(final Conversation.Level level) {
        return Conversation.of(leader, level);
    }

}
