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

package com.arangodb.next.api.reactive.database.impl;


import com.arangodb.next.api.reactive.ArangoDatabase;
import com.arangodb.next.api.reactive.database.DatabaseApi;
import com.arangodb.next.api.reactive.impl.ClientImpl;
import com.arangodb.next.api.reactive.impl.DeserializationTypes;
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.option.DBCreateOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.arangodb.next.api.util.ArangoResponseField.RESULT;


/**
 * @author Michele Rastelli
 */
public final class DatabaseApiImpl extends ClientImpl implements DatabaseApi {

    private static final String PATH_API = "/_api/database";
    private final ArangoDatabase db;

    public DatabaseApiImpl(final ArangoDatabase arangoDatabase) {
        super((ClientImpl) arangoDatabase);
        db = arangoDatabase;
    }

    @Override
    public Mono<Void> createDatabase(final DBCreateOptions options) {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(db.name())
                        .requestType(ArangoRequest.RequestType.POST)
                        .path(PATH_API)
                        .body(getSerde().serialize(options))
                        .build()
        )
                .then();
    }

    @Override
    public Mono<DatabaseEntity> getDatabase(final String dbName) {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(dbName)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API + "/current")
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserializeField(RESULT, bytes, DatabaseEntity.class));
    }

    @Override
    public Flux<String> getDatabases() {
        return getDatabasesFromPath(PATH_API);
    }

    @Override
    public Flux<String> getAccessibleDatabases() {
        return getDatabasesFromPath(PATH_API + "/user");
    }

    private Flux<String> getDatabasesFromPath(final String path) {
        return getCommunication().execute(
                ArangoRequest.builder()
                        .database(db.name())
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(path)
                        .build()
        )
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().<Iterable<String>>deserializeField(RESULT, bytes, DeserializationTypes.ITERABLE_OF_STRING))
                .flatMapMany(Flux::fromIterable);
    }

}
