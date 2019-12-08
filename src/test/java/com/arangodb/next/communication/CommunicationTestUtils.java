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

package com.arangodb.next.communication;

import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.connection.ConnectionTestUtils;
import com.arangodb.next.connection.IOUtils;
import com.arangodb.velocypack.VPackSlice;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class CommunicationTestUtils {

    static void executeRequest(ArangoCommunication communication, int retries) {
        ArangoResponse response = communication.execute(ConnectionTestUtils.versionRequest)
                .retry(retries, t -> t instanceof IOException || t instanceof TimeoutException)
                .block();
        verifyAssertions(response);
    }

    static void executeRequest(ArangoCommunication communication) {
        ArangoResponse response = communication.execute(ConnectionTestUtils.versionRequest).block();
        verifyAssertions(response);
    }

    private static void verifyAssertions(ArangoResponse response) {
        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(IOUtils.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");

        response.getBody().release();
    }

}
