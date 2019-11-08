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

package com.arangodb.next.connection.vst;

import com.arangodb.next.connection.*;
import com.arangodb.velocypack.VPackBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.ValueType;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import utils.EchoHttpServer;
import utils.EchoTcpServer;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TODO: run authentication tests against the following matrix:
 * - server:
 * - - ARANGO_NO_AUTH
 * - - ARANGO_ROOT_PASSWORD
 * - authenticationMethod:
 * - - basic
 * - - jwt
 * - connection:
 * - - http
 * - - vst
 *
 * @author Michele Rastelli
 */
class ConnectionTest {

    private ConnectionConfig config = ConnectionConfig.builder()
            .host(HostDescription.of("172.28.3.1", 8529))
            .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
            .contentType(ContentType.VPACK)
            .build();

    private ArangoRequest request = ArangoRequest.builder()
            .database("_system")
            .path("/_api/version")
            .requestType(RequestType.GET)
            .body(Unpooled.EMPTY_BUFFER)
            .build();

    @Test
    void getVersion() {
        ArangoResponse response = new VstConnection(config).initialize()
                .flatMap(connection -> connection.execute(ArangoRequest.builder().from(request).body(IOUtils.createBuffer()).build()))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");
        response.getBody().release();
    }


    @Test
    @Disabled
    void inifiniteParallelLoop() {
        ArangoConnection connection = new VstConnection(config).initialize().block();
        Flux.fromStream(Stream.iterate(0, i -> i + 1))
                .flatMap(i -> connection.execute(ArangoRequest.builder().from(request).body(IOUtils.createBuffer()).build()))
                .doOnNext(v -> {
                    new VPackSlice(IOUtilsTest.getByteArray(v.getBody()));
                    v.getBody().release();
                })
                .then().block();
    }

}