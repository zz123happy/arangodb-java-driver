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
import com.arangodb.next.api.database.DatabaseApi;
import com.arangodb.next.api.database.impl.DatabaseApiImpl;
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.entity.model.Engine;
import com.arangodb.next.entity.model.ServerRole;
import com.arangodb.next.entity.model.Version;
import com.arangodb.next.entity.serde.DeserializationTypes;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.arangodb.next.api.util.ArangoResponseField.RESULT;

/**
 * @author Michele Rastelli
 */
public final class ArangoDatabaseImpl extends ArangoClientImpl implements ArangoDatabase {

    private static final String PATH_API_USER = "/_api/user";
    private static final String PATH_API_VERSION = "/_api/version";
    private static final String PATH_API_ENGINE = "/_api/engine";
    private static final String PATH_API_ROLE = "/_admin/server/role";

    private final ArangoDB arango;
    private final String name;

    public ArangoDatabaseImpl(final ArangoDB arangoDB, final String dbName) {
        super((ArangoClientImpl) arangoDB);
        arango = arangoDB;
        name = dbName;
    }

    @Override
    public ArangoDB arango() {
        return arango;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DatabaseApi databaseApi() {
        return new DatabaseApiImpl(this);
    }

    @Override
    public Mono<Version> getVersion() {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(name)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_VERSION)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserialize(bytes, Version.class));
    }

    @Override
    public Mono<Engine> getEngine() {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(name)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_ENGINE)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserialize(bytes, Engine.class));
    }

    @Override
    public Mono<ServerRole> getRole() {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(name)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_ROLE)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserializeField("role", bytes, ServerRole.class));
    }

    @Override
    public Flux<String> getAccessibleDatabasesFor(final String user) {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(name())
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_USER + "/" + user + "/database")
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().<Map<String, String>>deserializeField(RESULT, bytes, DeserializationTypes.MAP_OF_STRING_STRING))
                .map(Map::keySet)
                .flatMapMany(Flux::fromIterable);
    }

}
