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

package com.arangodb.next.connection;


import com.arangodb.next.entity.Version;
import com.arangodb.next.entity.codec.ArangoDeserializer;
import com.arangodb.velocypack.VPackBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.ValueType;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
public class ConnectionTestUtils {
    public static final ConnectionSchedulerFactory DEFAULT_SCHEDULER_FACTORY = new ConnectionSchedulerFactory(4);

    public static final ArangoRequest versionRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/version")
            .requestType(ArangoRequest.RequestType.GET)
            .putQueryParam("details", "true")
            .build();

    /**
     * @param slice input
     * @return a byte array from VPackSlice buffer, truncating final null bytes
     */
    private static byte[] extractBytes(final VPackSlice slice) {
        return Arrays.copyOf(slice.getBuffer(), slice.getByteSize());
    }

    public static ArangoRequest postRequest() {
        return ArangoRequest.builder()
                .database("_system")
                .path("/_api/query")
                .requestType(ArangoRequest.RequestType.POST)
                .body(extractBytes(createParseQueryRequestBody()))
                .build();
    }

    public static void performRequest(ArangoConnection connection, int retries) {
        ArangoResponse response = connection.execute(versionRequest)
                .retry(retries, t -> t instanceof IOException || t instanceof TimeoutException)
                .block();
        verifyGetResponseVPack(response);
    }

    public static void performRequest(ArangoConnection connection) {
        ArangoResponse response = connection.execute(versionRequest).block();
        verifyGetResponseVPack(response);
    }

    public static void verifyGetResponseVPack(ArangoResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        ArangoDeserializer deserializer = ArangoDeserializer.of(ContentType.VPACK);
        Version version = deserializer.deserialize(response.getBody(), Version.class);
        assertThat(version.getServer()).isEqualTo("arango");
    }

    public static void verifyPostResponseVPack(ArangoResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(response.getBody());
        assertThat(responseBodySlice.get("parsed").getAsBoolean()).isEqualTo(true);
    }

    private static VPackSlice createParseQueryRequestBody() {
        final VPackBuilder builder = new VPackBuilder();
        builder.add(ValueType.OBJECT);
        builder.add("query", "FOR i IN 1..100 RETURN i * 3");
        builder.close();
        return builder.slice();
    }

}
