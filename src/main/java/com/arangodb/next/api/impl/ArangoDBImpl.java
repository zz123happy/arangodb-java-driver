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
import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.serde.ArangoSerde;
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
    public Mono<DatabaseEntity> getDatabase(String name) {
        return communication.execute(
                ArangoRequest.builder()
                        .database(name)
                        .requestType(ArangoRequest.RequestType.GET)
                        .path(PATH_API_DATABASE + "/current")
                        .build()
        )
                // TODO: use this type:
                // new ModifiableSuccessEntity<DatabaseEntity>() {}.getClass().getGenericSuperclass()
                // TODO: also overload deserializer.deserialize(byte[], Type)
                .map(response -> serde.deserialize(response.getBody(), DatabaseEntity.class));
    }

}
