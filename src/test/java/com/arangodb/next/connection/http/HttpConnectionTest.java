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

package com.arangodb.next.connection.http;

import com.arangodb.next.connection.*;
import com.arangodb.next.connection.vst.RequestType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import utils.EchoHttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class HttpConnectionTest {

    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void close() {
    }

    @Test
    void execute() {
    }

    @Test
    void jwtAuthentication(){
        new EchoHttpServer().start().join();

        ConnectionConfig config = ConnectionConfig.builder()
                .host(HostDescription.of("localhost", 9000))
                .authenticationMethod(AuthenticationMethod.ofJwt("token"))
                .contentType(ContentType.JSON)
                .build();

        HttpConnection connection = new HttpConnection(config);

        Request request = Request.builder()
                .database("database")
                .path("path")
                .requestType(RequestType.GET)
                .body()
                .build();

        Response response = connection.execute(request).block();
        assert response != null;

        assertThat(response.getMeta()).containsKey("authorization");
        assertThat(response.getMeta().get("authorization")).isEqualTo("Bearer token");
    }

}