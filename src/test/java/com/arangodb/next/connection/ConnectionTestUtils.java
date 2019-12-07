package com.arangodb.next.connection;/*
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


import com.arangodb.velocypack.VPackSlice;

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

    public static void performRequest(ArangoConnection connection, int retries) {
        ArangoResponse response = connection.execute(versionRequest).retry(retries).block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(IOUtils.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");

        response.getBody().release();
    }

    public static void performRequest(ArangoConnection connection) {
        performRequest(connection, 0);
    }

}
