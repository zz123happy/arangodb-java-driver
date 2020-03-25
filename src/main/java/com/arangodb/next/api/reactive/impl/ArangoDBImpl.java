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


package com.arangodb.next.api.reactive.impl;

import com.arangodb.next.api.reactive.ArangoDB;
import com.arangodb.next.api.reactive.ArangoDatabase;
import com.arangodb.next.api.reactive.ConversationManager;
import com.arangodb.next.api.sync.ArangoDBSync;
import com.arangodb.next.api.sync.impl.ArangoDBSyncImpl;
import com.arangodb.next.communication.ArangoCommunication;
import com.arangodb.next.communication.CommunicationConfig;
import com.arangodb.next.entity.serde.ArangoSerde;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 */
public final class ArangoDBImpl implements ArangoDB {

    private final ConversationManager conversationManager;
    private final ArangoCommunication communication;
    private final ArangoSerde serde;

    public ArangoDBImpl(final CommunicationConfig config) {
        communication = ArangoCommunication.create(config).block();
        serde = ArangoSerde.of(config.getContentType());
        conversationManager = new ConversationManagerImpl(communication);
    }

    @Override
    public ArangoDBSync sync() {
        return new ArangoDBSyncImpl(this);
    }

    @Override
    public ArangoDatabase db(final String name) {
        return new ArangoDatabaseImpl(this, name);
    }

    @Override
    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    @Override
    public Mono<Void> shutdown() {
        return communication.close();
    }

    public ArangoCommunication getCommunication() {
        return communication;
    }

    public ArangoSerde getSerde() {
        return serde;
    }

}
