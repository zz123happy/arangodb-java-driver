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

package com.arangodb.next.api;

import com.arangodb.next.api.utils.ArangoDBProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.next.entity.model.Sharding;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.option.DatabaseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Michele Rastelli
 */
class ArangoDBTest {


    @Test
    void shutdown() {
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void createDatabase(TestContext ctx, ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        arangoDB.createDatabase(name).block();
        DatabaseEntity db = arangoDB.getDatabase(name).block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.isSystem()).isFalse();

        if (ctx.isCluster()) {
            assertThat(db.getWriteConcern()).isEqualTo(1);
            assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void createDatabaseWithOptions(TestContext ctx, ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        arangoDB.createDatabase(DBCreateOptions
                .builder()
                .name(name)
                .options(DatabaseOptions.builder()
                        .sharding(Sharding.SINGLE)
                        .writeConcern(2)
                        .replicationFactor(ReplicationFactor.of(2))
                        .build())
                .build()).block();
        DatabaseEntity db = arangoDB.getDatabase(name).block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.isSystem()).isFalse();

        if (ctx.isCluster()) {
            assertThat(db.getWriteConcern()).isEqualTo(2);
            assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(2));
            assertThat(db.getSharding()).isEqualTo(Sharding.SINGLE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getDatabase(TestContext ctx, ArangoDB arangoDB) {
        DatabaseEntity db = arangoDB.getDatabase("_system").block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo("_system");
        assertThat(db.getPath()).isNotNull();
        assertThat(db.isSystem()).isTrue();

        if (ctx.isCluster()) {
            assertThat(db.getWriteConcern()).isEqualTo(1);
            assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }

}
