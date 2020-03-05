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

import com.arangodb.next.api.impl.ArangoDBImpl;
import com.arangodb.next.communication.CommunicationConfig;
import com.arangodb.next.connection.ArangoProtocol;
import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.ContentType;
import com.arangodb.next.connection.HostDescription;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.next.entity.model.Sharding;
import com.arangodb.next.entity.option.DBCreateOptions;
import com.arangodb.next.entity.option.DatabaseOptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * @author Michele Rastelli
 */
@Disabled
class ArangoDBTest {

    static class TargetProvider implements ArgumentsProvider {

        private static final CommunicationConfig config = CommunicationConfig.builder()
                .addHosts(HostDescription.of("coordinator1", 8529))
                .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
                .build();

        private static List<ArangoDB> targets = Stream
                .of(
                        CommunicationConfig.builder().from(config)
                                .protocol(ArangoProtocol.VST)
                                .contentType(ContentType.VPACK)
                                .build(),
                        CommunicationConfig.builder().from(config)
                                .protocol(ArangoProtocol.HTTP)
                                .contentType(ContentType.VPACK)
                                .build(),
                        CommunicationConfig.builder().from(config)
                                .protocol(ArangoProtocol.HTTP)
                                .contentType(ContentType.JSON)
                                .build()
                )
                .map(ArangoDBImpl::new)
                .collect(Collectors.toList());

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return targets.stream().map(Arguments::of);
        }
    }

    @Test
    void shutdown() {
    }

    @ParameterizedTest
    @ArgumentsSource(TargetProvider.class)
    void createDatabase(ArangoDB arangoDB) {
        String name = "db-" + UUID.randomUUID().toString();
        arangoDB.createDatabase(name).block();
        DatabaseEntity db = arangoDB.getDatabase(name).block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo(name);
        assertThat(db.getPath()).isNotNull();
        assertThat(db.getWriteConcern()).isEqualTo(1);
        assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
        assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        assertThat(db.isSystem()).isFalse();
    }


    @ParameterizedTest
    @ArgumentsSource(TargetProvider.class)
    void createDatabaseWithOptions(ArangoDB arangoDB) {
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
        assertThat(db.getWriteConcern()).isEqualTo(2);
        assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(2));
        assertThat(db.getSharding()).isEqualTo(Sharding.SINGLE);
        assertThat(db.isSystem()).isFalse();
    }

    @ParameterizedTest
    @ArgumentsSource(TargetProvider.class)
    void getDatabase(ArangoDB arangoDB) {
        DatabaseEntity db = arangoDB.getDatabase("_system").block();

        assertThat(db.getId()).isNotNull();
        assertThat(db.getName()).isEqualTo("_system");
        assertThat(db.getPath()).isNotNull();
        assertThat(db.getWriteConcern()).isEqualTo(1);
        assertThat(db.getReplicationFactor()).isEqualTo(ReplicationFactor.of(1));
        assertThat(db.getSharding()).isEqualTo(Sharding.FLEXIBLE);
        assertThat(db.isSystem()).isTrue();
    }

}
