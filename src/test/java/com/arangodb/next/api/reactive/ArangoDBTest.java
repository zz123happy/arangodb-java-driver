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

package com.arangodb.next.api.reactive;

import com.arangodb.next.api.utils.ArangoDBProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.entity.model.*;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.option.DatabaseOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
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
        DatabaseEntity db = arangoDB.getConversationManager().requireConversation(
                arangoDB
                        .createDatabase(name)
                        .then(arangoDB.getDatabase(name))
        ).block();

        assertThat(db).isNotNull();
        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.isSystem()).isFalse();

        if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
            assertThat(db.getWriteConcern()).isEqualTo(1);
            assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void createDatabaseWithOptions(TestContext ctx, ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity db = arangoDB.getConversationManager().requireConversation(
                arangoDB
                        .createDatabase(DBCreateOptions
                                .builder()
                                .name(name)
                                .options(DatabaseOptions.builder()
                                        .sharding(Sharding.SINGLE)
                                        .writeConcern(2)
                                        .replicationFactor(ReplicationFactor.of(2))
                                        .build())
                                .build())
                        .then(arangoDB.getDatabase(name))
        ).block();

        assertThat(db).isNotNull();
        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.isSystem()).isFalse();

        if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
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

        if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
            assertThat(db.getWriteConcern()).isEqualTo(1);
            assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getDatabases(TestContext ctx, ArangoDB arangoDB) {
        List<String> databases = arangoDB.getDatabases().collectList().block();
        assertThat(databases).isNotNull();
        assertThat(databases).contains("_system");
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getAccessibleDatabases(TestContext ctx, ArangoDB arangoDB) {
        List<String> databases = arangoDB.getAccessibleDatabases().collectList().block();
        assertThat(databases).isNotNull();
        assertThat(databases).contains("_system");
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getAccessibleDatabasesFor(TestContext ctx, ArangoDB arangoDB) {
        List<String> databases = arangoDB.getAccessibleDatabasesFor("root").collectList().block();
        assertThat(databases).isNotNull();
        assertThat(databases).contains("_system");
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getVersion(TestContext ctx, ArangoDB arangoDB) {
        Version version = arangoDB.getVersion().block();
        assertThat(version).isNotNull();
        assertThat(version.getServer()).isNotNull();
        assertThat(version.getVersion()).isNotNull();
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getEngine(TestContext ctx, ArangoDB arangoDB) {
        Engine engine = arangoDB.getEngine().block();
        assertThat(engine).isNotNull();
        assertThat(engine.getName()).isEqualTo(Engine.StorageEngineName.ROCKSDB);
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getRole(TestContext ctx, ArangoDB arangoDB) {
        ServerRole role = arangoDB.getRole().block();
        assertThat(role).isNotNull();
    }

}
