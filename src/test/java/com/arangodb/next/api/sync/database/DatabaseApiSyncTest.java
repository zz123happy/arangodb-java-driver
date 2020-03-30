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

package com.arangodb.next.api.sync.database;

import com.arangodb.next.api.sync.ArangoDBSync;
import com.arangodb.next.api.sync.ThreadConversation;
import com.arangodb.next.api.utils.ArangoDBSyncProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.next.entity.model.Sharding;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.option.DatabaseOptions;
import com.arangodb.next.exceptions.ArangoServerException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


/**
 * @author Michele Rastelli
 */
class DatabaseApiSyncTest {

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBSyncProvider.class)
    void createDatabase(TestContext ctx, ArangoDBSync arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity db;
        try (ThreadConversation tc = arangoDB.getConversationManager().requireConversation()) {
            arangoDB.db().databaseApi().createDatabase(name);
            db = arangoDB.db().databaseApi().getDatabase(name);
        }

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
    @ArgumentsSource(ArangoDBSyncProvider.class)
    void createAndDeleteDatabaseWithOptions(TestContext ctx, ArangoDBSync arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity db;
        try (ThreadConversation tc = arangoDB.getConversationManager().requireConversation()) {
            arangoDB.db()
                    .databaseApi()
                    .createDatabase(DBCreateOptions
                            .builder()
                            .name(name)
                            .options(DatabaseOptions.builder()
                                    .sharding(Sharding.SINGLE)
                                    .writeConcern(2)
                                    .replicationFactor(ReplicationFactor.of(2))
                                    .build())
                            .build());
            db = arangoDB.db().databaseApi().getDatabase(name);

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

            arangoDB.db().databaseApi().dropDatabase(name);

            // get database
            Throwable thrown = catchThrowable(() -> arangoDB.db().databaseApi().getDatabase(name));

            assertThat(thrown).isInstanceOf(ArangoServerException.class);
            assertThat(thrown.getMessage()).contains("database not found");
            assertThat(((ArangoServerException) thrown).getResponseCode()).isEqualTo(404);
            assertThat(((ArangoServerException) thrown).getEntity().getErrorNum()).isEqualTo(1228);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBSyncProvider.class)
    void getDatabase(TestContext ctx, ArangoDBSync arangoDB) {
        DatabaseEntity db = arangoDB.db().databaseApi().getDatabase("_system");

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

}