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

package com.arangodb.next.connection.vst;

import com.arangodb.next.connection.ArangoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
class MessageStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageStore.class);

    private final Map<Long, MonoProcessor<ArangoResponse>> pendingRequests = new HashMap<>();

    /**
     * Adds a pending request to the store
     *
     * @param messageId id of the sent message
     * @return a {@link Mono} that will be resolved when the related response is received
     */
    Mono<ArangoResponse> add(long messageId) {
        if (pendingRequests.containsKey(messageId)) {
            throw new IllegalStateException("Key already present: " + messageId);
        }
        final MonoProcessor<ArangoResponse> response = MonoProcessor.create();
        pendingRequests.put(messageId, response);
        return response;
    }

    /**
     * Resolves the pending request related to the messageId
     *
     * @param messageId id of the received message
     * @param response the received response
     */
    void resolve(long messageId, final ArangoResponse response) {
        LOGGER.debug("Received Message (id={})", messageId);
        final MonoProcessor<ArangoResponse> future = pendingRequests.remove(messageId);
        if (future == null) {
            throw new IllegalStateException("No pending request found for received message: " + messageId);
        }
        future.onNext(response);
    }

    // TODO: check if this is really necessary, atm it should be only used for the reply to the authentication request?!
    void cancel(long messageId) {
        final MonoProcessor<ArangoResponse> future = pendingRequests.remove(messageId);
        if (future != null) {
            LOGGER.error(String.format("Cancel Message unexpected (id=%s).", messageId));
            future.onError(new RuntimeException("Cancelled!"));
        }
    }

    /**
     * Completes exceptionally all the pending requests
     *
     * @param t cause
     */
    void clear(final Throwable t) {
        pendingRequests.values().forEach(future -> future.onError(t));
        pendingRequests.clear();
    }

}
