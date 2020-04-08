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

package com.arangodb.next.api.collection.impl;


import com.arangodb.next.api.collection.CollectionApi;
import com.arangodb.next.api.collection.entity.*;
import com.arangodb.next.api.reactive.ArangoDatabase;
import com.arangodb.next.api.reactive.impl.ArangoClientImpl;
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.connection.ImmutableArangoRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Type;

import static com.arangodb.next.api.util.ArangoResponseField.RESULT;

/**
 * @author Michele Rastelli
 */
public final class CollectionApiImpl extends ArangoClientImpl implements CollectionApi {

    private static final String PATH_API = "/_api/collection";

    public static final Type ITERABLE_OF_COLLECTION_ENTITY = new com.arangodb.velocypack.Type<Iterable<CollectionEntity>>() {
    }.getType();

    private final String dbName;

    public CollectionApiImpl(final ArangoDatabase arangoDatabase) {
        super((ArangoClientImpl) arangoDatabase);
        dbName = arangoDatabase.name();
    }

    @Override
    public Flux<CollectionEntity> getCollections(final CollectionsReadParams params) {
        ImmutableArangoRequest.Builder requestBuilder = ArangoRequest.builder()
                .database(dbName)
                .requestType(ArangoRequest.RequestType.GET)
                .path(PATH_API);

        params.getExcludeSystem()
                .ifPresent(excludeSystem ->
                        requestBuilder.putQueryParam(
                                CollectionsReadParams.EXCLUDE_SYSTEM_PARAM,
                                String.valueOf(excludeSystem)
                        )
                );

        return getCommunication()
                .execute(requestBuilder.build())
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().<Iterable<CollectionEntity>>deserializeField(RESULT, bytes, ITERABLE_OF_COLLECTION_ENTITY))
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<CollectionEntityDetailed> createCollection(
            final CollectionCreateOptions options,
            final CollectionCreateParams params
    ) {
        ImmutableArangoRequest.Builder requestBuilder = ArangoRequest.builder()
                .database(dbName)
                .requestType(ArangoRequest.RequestType.POST)
                .body(getSerde().serialize(options))
                .path(PATH_API);

        params.getEnforceReplicationFactor()
                .ifPresent(enforceReplicationFactor ->
                        requestBuilder.putQueryParam(
                                CollectionCreateParams.ENFORCE_REPLICATION_FACTOR_PARAM,
                                String.valueOf(enforceReplicationFactor.getValue())
                        )
                );

        params.getWaitForSyncReplication()
                .ifPresent(waitForSyncReplication ->
                        requestBuilder.putQueryParam(
                                CollectionCreateParams.WAIT_FOR_SYNC_REPLICATION_PARAM,
                                String.valueOf(waitForSyncReplication.getValue())
                        )
                );

        return getCommunication()
                .execute(requestBuilder.build())
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserialize(bytes, CollectionEntityDetailed.class));
    }

    @Override
    public Mono<Void> dropCollection(final String name, final CollectionDropParams params) {
        ImmutableArangoRequest.Builder requestBuilder = ArangoRequest.builder()
                .database(dbName)
                .requestType(ArangoRequest.RequestType.DELETE)
                .path(PATH_API + "/" + name);

        params.getIsSystem()
                .ifPresent(isSystem ->
                        requestBuilder.putQueryParam(
                                CollectionDropParams.IS_SYSTEM_PARAM,
                                String.valueOf(isSystem)
                        )
                );

        return getCommunication()
                .execute(requestBuilder.build())
                .then();
    }

    @Override
    public Mono<CollectionEntity> getCollectionInfo(final String name) {
        return getCommunication()
                .execute(ArangoRequest.builder()
                        .database(dbName)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API + "/" + name)
                        .build())
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserialize(bytes, CollectionEntity.class));
    }

    @Override
    public Mono<CollectionEntityDetailed> getCollectionProperties(final String name) {
        return getCommunication()
                .execute(ArangoRequest.builder()
                        .database(dbName)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API + "/" + name + "/properties")
                        .build())
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserialize(bytes, CollectionEntityDetailed.class));
    }

    @Override
    public Mono<Long> count(final String name) {
        return getCommunication()
                .execute(ArangoRequest.builder()
                        .database(dbName)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API + "/" + name + "/count")
                        .build())
                .map(ArangoResponse::getBody)
                .map(bytes -> getSerde().deserializeField("count", bytes, Long.class));
    }

}
