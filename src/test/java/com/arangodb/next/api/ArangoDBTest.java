package com.arangodb.next.api;

import com.arangodb.next.api.impl.ArangoDBImpl;
import com.arangodb.next.communication.CommunicationConfig;
import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.HostDescription;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.next.entity.model.Sharding;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.option.DatabaseOptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
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


/**
 * @author Michele Rastelli
 */
//@Disabled

class ArangoDBTest {

    private static final CommunicationConfig config = CommunicationConfig.builder()
//            .contentType(ContentType.JSON)
            .addHosts(HostDescription.of("coordinator1", 8529))
            .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
            .build();

    private final ArangoDB arangoDB;

    ArangoDBTest() {
        arangoDB = new ArangoDBImpl(config);
    }

    @Test
    void shutdown() {
    }

    @Test
    void createDatabase() {
        arangoDB.createDatabase("db-" + UUID.randomUUID().toString()).block();
    }


    @Test
    void createDatabaseWithOptions() {
        String name = "db-" + UUID.randomUUID().toString();
        arangoDB.createDatabase(DBCreateOptions
                .builder()
                .name(name)
                .options(DatabaseOptions.builder()
                        .sharding(Sharding.single)
                        .writeConcern(2)
                        .replicationFactor(ReplicationFactor.of(2))
                        .build())
                .build()).block();
        DatabaseEntity db = arangoDB.getDatabase(name).block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.getWriteConcern()).isEqualTo(2);
        assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(2));
        assertThat(db.getSharding()).isEqualTo(Sharding.single);
        assertThat(db.isSystem()).isFalse();
    }

    @Test
    void getDatabase() {
        DatabaseEntity db = arangoDB.getDatabase("_system").block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo("_system");
        assertThat(db.getPath()).isNotNull();
        assertThat(db.getWriteConcern()).isEqualTo(2);
        assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(2));
        assertThat(db.getSharding()).isEqualTo(Sharding.single);
        assertThat(db.isSystem()).isFalse();
    }

}