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
import com.arangodb.next.api.reactive.ConversationManager;
import com.arangodb.next.communication.ArangoCommunication;
import com.arangodb.next.communication.CommunicationConfig;
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.Engine;
import com.arangodb.next.entity.model.ServerRole;
import com.arangodb.next.entity.model.Version;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.serde.ArangoSerde;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * @author Michele Rastelli
 */
public final class ArangoDBImpl implements ArangoDB {

    private static final String PATH_API_DATABASE = "/_api/database";
    private static final String PATH_API_USER = "/_api/user";
    private static final String PATH_API_VERSION = "/_api/version";
    private static final String PATH_API_ENGINE = "/_api/engine";
    private static final String PATH_API_ROLE = "/_admin/server/role";
    public static final String RESULT = "result";

    private final ConversationManager conversationManager;
    private final ArangoCommunication communication;
    private final ArangoSerde serde;

    private final Type iterableOfStringType = new com.arangodb.velocypack.Type<Iterable<String>>() {
    }.getType();

    private final Type mapOfStringStringType = new com.arangodb.velocypack.Type<Map<String, String>>() {
    }.getType();

    public ArangoDBImpl(final CommunicationConfig config) {
        communication = ArangoCommunication.create(config).block();
        serde = ArangoSerde.of(config.getContentType());
        conversationManager = new ConversationManagerImpl(communication);
    }

    @Override
    public ConversationManager getConversationManager() {
        return conversationManager;
    }

    @Override
    public Mono<Void> shutdown() {
        return communication.close();
    }

    @Override
    public Mono<Void> createDatabase(final DBCreateOptions options) {
        return communication.execute(
                ArangoRequest.builder()
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
                .map(bytes -> serde.deserializeField(RESULT, bytes, DatabaseEntity.class));
    }

    @Override
    public Flux<String> getDatabases() {
        return getDatabasesFromPath(PATH_API_DATABASE);
    }

    @Override
    public Flux<String> getAccessibleDatabases() {
        return getDatabasesFromPath(PATH_API_DATABASE + "/user");
    }

    @Override
    public Flux<String> getAccessibleDatabasesFor(String user) {
        return communication.execute(
                ArangoRequest.builder()
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_USER + "/" + user + "/database")
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> serde.<Map<String, String>>deserializeField(RESULT, bytes, mapOfStringStringType))
                .map(Map::keySet)
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Version> getVersion() {
        return communication.execute(
                ArangoRequest.builder()
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_VERSION)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> serde.deserialize(bytes, Version.class));
    }

    @Override
    public Mono<Engine> getEngine() {
        return communication.execute(
                ArangoRequest.builder()
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_ENGINE)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> serde.deserialize(bytes, Engine.class));
    }

    @Override
    public Mono<ServerRole> getRole() {
        return communication.execute(
                ArangoRequest.builder()
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_ROLE)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> serde.deserializeField("role", bytes, ServerRole.class));
    }

    private Flux<String> getDatabasesFromPath(String path) {
        return communication.execute(
                ArangoRequest.builder()
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(path)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> serde.<Iterable<String>>deserializeField(RESULT, bytes, iterableOfStringType))
                .flatMapMany(Flux::fromIterable);
    }

}
