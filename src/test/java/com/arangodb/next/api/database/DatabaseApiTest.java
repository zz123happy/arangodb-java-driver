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


import com.arangodb.next.api.reactive.ArangoDB;
import com.arangodb.next.api.utils.ArangoDBProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.communication.Conversation;
import com.arangodb.next.api.database.entity.DatabaseEntity;
import com.arangodb.next.api.entity.ReplicationFactor;
import com.arangodb.next.api.entity.Sharding;
import com.arangodb.next.api.database.entity.DatabaseCreateOptions;
import com.arangodb.next.exceptions.ArangoServerException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * @author Michele Rastelli
 */
class DatabaseApiTest {

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void createDatabase(TestContext ctx, ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        DatabaseEntity db = arangoDB.getConversationManager().requireConversation(
                arangoDB
                        .db()
                        .databaseApi()
                        .createDatabase(name)
                        .then(arangoDB.db().databaseApi().getDatabase(name))
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
    void createAndDeleteDatabaseWithOptions(TestContext ctx, ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();

        Conversation conversation = arangoDB.getConversationManager().createConversation(Conversation.Level.REQUIRED);

        // create database
        arangoDB.getConversationManager().useConversation(conversation,
                arangoDB.db().databaseApi().createDatabase(
                        DatabaseCreateOptions.builder()
                                .name(name)
                                .options(DatabaseCreateOptions.Options.builder()
                                        .sharding(Sharding.SINGLE)
                                        .writeConcern(2)
                                        .replicationFactor(ReplicationFactor.of(2))
                                        .build())
                                .build())
        ).block();

        // get database
        arangoDB.getConversationManager().useConversation(conversation,
                arangoDB.db().databaseApi().getDatabase(name)
                        .doOnNext(db -> {
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
                        })
        ).block();

        // drop database
        arangoDB.getConversationManager().useConversation(conversation,
                arangoDB.db().databaseApi().dropDatabase(name)
        ).block();

        // get database
        Throwable thrown = catchThrowable(() ->
                arangoDB.getConversationManager().useConversation(conversation,
                        arangoDB.db().databaseApi().getDatabase(name)
                ).block());

        assertThat(thrown).isInstanceOf(ArangoServerException.class);
        assertThat(thrown.getMessage()).contains("database not found");
        assertThat(((ArangoServerException) thrown).getResponseCode()).isEqualTo(404);
        assertThat(((ArangoServerException) thrown).getEntity().getErrorNum()).isEqualTo(1228);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getDatabase(TestContext ctx, ArangoDB arangoDB) {
        DatabaseEntity db = arangoDB.db().databaseApi().getDatabase("_system").block();

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
        List<String> databases = arangoDB.db().databaseApi().getDatabases().collectList().block();
        assertThat(databases).isNotNull();
        assertThat(databases).contains("_system");
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(ArangoDBProvider.class)
    void getAccessibleDatabases(TestContext ctx, ArangoDB arangoDB) {
        List<String> databases = arangoDB.db().databaseApi().getAccessibleDatabases().collectList().block();
        assertThat(databases).isNotNull();
        assertThat(databases).contains("_system");
    }

}
