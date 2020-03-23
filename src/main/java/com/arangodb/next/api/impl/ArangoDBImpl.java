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


package com.arangodb.next.api.impl;

import com.arangodb.next.api.ArangoDB;
import com.arangodb.next.communication.ArangoCommunication;
import com.arangodb.next.communication.CommunicationConfig;
import com.arangodb.next.communication.Conversation;
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.serde.ArangoSerde;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 */
public final class ArangoDBImpl implements ArangoDB {

    private static final String DB = "_system";
    private static final String PATH_API_DATABASE = "/_api/database";

    private final ArangoCommunication communication;
    private final ArangoSerde serde;

    public ArangoDBImpl(final CommunicationConfig config) {
        communication = ArangoCommunication.create(config).block();
        serde = ArangoSerde.of(config.getContentType());
    }

    //region CONVERSATION MANAGEMENT
    @Override
    public Conversation createConversation(Conversation.Level level) {
        return communication.createConversation(level);
    }

    @Override
    public <T> Mono<T> requireConversation(Mono<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.REQUIRED));
    }

    @Override
    public <T> Flux<T> requireConversation(Flux<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.REQUIRED));
    }

    @Override
    public <T> Mono<T> preferConversation(Mono<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.PREFERRED));
    }

    @Override
    public <T> Flux<T> preferConversation(Flux<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.PREFERRED));
    }

    private <T> Mono<T> wrapInConversation(Mono<T> publisher, Conversation conversation) {
        return publisher.subscriberContext(sCtx -> sCtx.put(ArangoCommunication.CONVERSATION_CTX, conversation));
    }

    private <T> Flux<T> wrapInConversation(Flux<T> publisher, Conversation conversation) {
        return publisher.subscriberContext(sCtx -> sCtx.put(ArangoCommunication.CONVERSATION_CTX, conversation));
    }
    //endregion

    @Override
    public Mono<Void> shutdown() {
        return communication.close();
    }

    @Override
    public Mono<Void> createDatabase(final DBCreateOptions options) {
        return communication.execute(
                ArangoRequest.builder()
                        .database(DB)
                        .requestType(ArangoRequest.RequestType.POST)
                        .path(PATH_API_DATABASE)
                        .body(serde.serialize(options))
                        .build()
        )
                .then();
    }

    @Override
    public Mono<DatabaseEntity> getDatabase(final String name) {
        return communication.execute(
                ArangoRequest.builder()
                        .database(name)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_DATABASE + "/current")
                        .build()
        )
                .map(ArangoResponse::getBody)
                // TODO: mv all VPack related operations inside serde classes
                .map(serde::createVPackSlice)
                .map(it -> it.get("result"))
                .map(slice -> serde.deserialize(slice, DatabaseEntity.class));
    }

}
