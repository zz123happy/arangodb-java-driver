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

package com.arangodb.next.api.database;

import com.arangodb.next.api.database.entity.DatabaseCreateOptions;
import com.arangodb.next.api.database.entity.DatabaseEntity;
import com.arangodb.next.api.entity.ReplicationFactor;
import com.arangodb.next.api.database.entity.Sharding;
import com.arangodb.next.api.sync.ThreadConversation;
import com.arangodb.next.api.utils.DatabaseApiSyncProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.exceptions.server.ArangoServerException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


/**
 * @author Michele Rastelli
 */
@Tag("api")
class DatabaseApiSyncTest {

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(DatabaseApiSyncProvider.class)
    void createDatabase(TestContext ctx, DatabaseApiSync db) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity dbEntity;
        try (ThreadConversation tc = db.getConversationManager().requireConversation()) {
            db.createDatabase(name);
            dbEntity = db.getDatabase(name);
        }

        assertThat(dbEntity).isNotNull();
        assertThat(dbEntity.getId()).isNotNull();
        assertThat(dbEntity.getName()).isEqualTo(name);
        assertThat(dbEntity.getPath()).isNotNull();
        assertThat(dbEntity.isSystem()).isFalse();

        if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
            assertThat(dbEntity.getWriteConcern()).isEqualTo(1);
            assertThat(dbEntity.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(dbEntity.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(DatabaseApiSyncProvider.class)
    void createAndDeleteDatabaseWithOptions(TestContext ctx, DatabaseApiSync db) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity dbEntity;
        try (ThreadConversation tc = db.getConversationManager().requireConversation()) {
            db.createDatabase(DatabaseCreateOptions
                    .builder()
                    .name(name)
                    .options(DatabaseCreateOptions.Options.builder()
                            .sharding(Sharding.SINGLE)
                            .writeConcern(2)
                            .replicationFactor(ReplicationFactor.of(2))
                            .build())
                    .build());
            dbEntity = db.getDatabase(name);

            assertThat(dbEntity).isNotNull();
            assertThat(dbEntity.getId()).isNotNull();
            assertThat(dbEntity.getName()).isEqualTo(name);
            assertThat(dbEntity.getPath()).isNotNull();
            assertThat(dbEntity.isSystem()).isFalse();

            if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
                assertThat(dbEntity.getWriteConcern()).isEqualTo(2);
                assertThat(dbEntity.getReplicationFactor()).isEqualTo(ReplicationFactor.of(2));
                assertThat(dbEntity.getSharding()).isEqualTo(Sharding.SINGLE);
            }

            db.dropDatabase(name);

            // get database
            Throwable thrown = catchThrowable(() -> db.getDatabase(name));

            assertThat(thrown).isInstanceOf(ArangoServerException.class);
            assertThat(thrown.getMessage()).contains("database not found");
            assertThat(((ArangoServerException) thrown).getResponseCode()).isEqualTo(404);
            assertThat(((ArangoServerException) thrown).getEntity().getErrorNum()).isEqualTo(1228);
        }
    }


    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(DatabaseApiSyncProvider.class)
    void getDatabase(TestContext ctx, DatabaseApiSync db) {
        DatabaseEntity dbEntity = db.getDatabase("_system");

        assertThat(dbEntity.getId()).isNotNull();
        assertThat(dbEntity.getName()).isEqualTo("_system");
        assertThat(dbEntity.getPath()).isNotNull();
        assertThat(dbEntity.isSystem()).isTrue();

        if (ctx.isCluster() && ctx.isAtLeastVersion(3, 6)) {
            assertThat(dbEntity.getWriteConcern()).isEqualTo(1);
            assertThat(dbEntity.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
            assertThat(dbEntity.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        }
    }

}