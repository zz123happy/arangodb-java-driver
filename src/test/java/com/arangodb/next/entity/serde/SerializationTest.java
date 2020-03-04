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

package com.arangodb.next.entity.serde;

import com.arangodb.next.connection.ContentType;
import com.arangodb.next.entity.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class SerializationTest {

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void version(ContentType contentType) {
        Version original = ImmutableVersion.builder()
                .server("server")
                .version("version")
                .license("license")
                .putDetails("bla", "bla")
                .build();

        ArangoSerde serde = ArangoSerde.of(contentType);
        byte[] serialized = serde.serialize(original);
        Version deserialized = serde.deserialize(serialized, Version.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void clusterEndpoints(ContentType contentType) {
        ClusterEndpoints original =
                ImmutableClusterEndpoints.builder()
                        .error(false)
                        .code(200)
                        .addEndpoints(
                                ClusterEndpointsEntry.of("tcp://172.28.3.1:8529"),
                                ClusterEndpointsEntry.of("tcp://172.28.3.2:8529")
                        )
                        .build();

        ArangoSerde serde = ArangoSerde.of(contentType);
        byte[] serialized = serde.serialize(original);
        ClusterEndpoints deserialized = serde.deserialize(serialized, ClusterEndpoints.class);
        assertThat(deserialized).isEqualTo(original);
    }

    @ParameterizedTest
    @EnumSource(ContentType.class)
    void errorEntity(ContentType contentType) {
        ErrorEntity original =
                ImmutableErrorEntity.builder()
                        .error(false)
                        .code(200)
                        .errorNum(109)
                        .errorMessage("error 109")
                        .build();

        ArangoSerde serde = ArangoSerde.of(contentType);
        byte[] serialized = serde.serialize(original);
        ErrorEntity deserialized = serde.deserialize(serialized, ErrorEntity.class);
        assertThat(deserialized).isEqualTo(original);
    }

}