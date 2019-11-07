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
import org.junit.jupiter.api.Test;
import utils.EchoHttpServer;
import utils.EchoTcpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class VstConnectionTest {

    private ConnectionConfig config = ConnectionConfig.builder()
            .host(HostDescription.of("coordinator1", 8529))
            .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
            .contentType(ContentType.VPACK)
            .build();

    private ArangoRequest request = ArangoRequest.builder()
            .database("_system")
            .path("/_api/version")
            .requestType(RequestType.GET)
            .body(Unpooled.EMPTY_BUFFER)
            .build();

    @BeforeAll
    static void setup() {
        new EchoTcpServer().start().join();
    }

//    @Test
//    void execute() {
//        HttpConnection connection = new HttpConnection(config);
//        ArangoResponse response = connection.execute(request).block();
//
//        // authorization
//        assertThat(response).isNotNull();
//        assertThat(response.getMeta()).containsKey("authorization");
//        assertThat(response.getMeta().get("authorization")).isEqualTo("Bearer token");
//
//        // body
//        String receivedString = new String(IOUtilsTest.getByteArray(response.getBody()));
//        response.getBody().release();
//
//        assertThat(receivedString).isEqualTo(body);
//
//        // headers
//        assertThat(response.getMeta()).containsKey("headerParamKey");
//        assertThat(response.getMeta().get("headerParamKey")).isEqualTo("headerParamValue");
//
//        // accept header
//        assertThat(response.getMeta()).containsKey("accept");
//        assertThat(response.getMeta().get("accept")).isEqualTo("application/json");
//
//        // uri & params
//        assertThat(response.getMeta()).containsKey("uri");
//        assertThat(response.getMeta().get("uri")).isEqualTo("/_db/database/path?queryParamKey=queryParamValue");
//
//        // host
//        assertThat(response.getMeta()).containsKey("host");
//        assertThat(response.getMeta().get("host")).isEqualTo("localhost:9000");
//
//        // reponseCode
//        assertThat(response.getResponseCode()).isEqualTo(200);
//    }

//    @Test
//    void executeVPack() {
//        HttpConnection connection = new HttpConnection(ConnectionConfig.builder().from(config)
//                .contentType(ContentType.VPACK)
//                .build());
//
//        final VPackBuilder builder = new VPackBuilder();
//        builder.add(ValueType.OBJECT);
//        builder.add("message", "Hello World!");
//        builder.close();
//        final VPackSlice slice = builder.slice();
//
//        ArangoResponse response = connection.execute(ArangoRequest.builder().from(request)
//                .body(Unpooled.wrappedBuffer(slice.getBuffer()))
//                .build()).block();
//
//        // body
//        assertThat(response).isNotNull();
//        VPackSlice receivedSlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
//        response.getBody().release();
//
//        assertThat(receivedSlice).isEqualTo(slice);
//        assertThat(receivedSlice.get("message").getAsString()).isEqualTo("Hello World!");
//
//        // accept header
//        assertThat(response.getMeta()).containsKey("accept");
//        assertThat(response.getMeta().get("accept")).isEqualTo("application/x-velocypack");
//    }

    @Test
    void executeEmptyBody() {
        ArangoConnection connection = new VstConnection(config).initialize().block();
        ArangoResponse response = connection.execute(ArangoRequest.builder().from(request).body(IOUtils.createBuffer()).build()).block();

        // body
        assertThat(response).isNotNull();

        System.out.println(new VPackSlice(IOUtilsTest.getByteArray(response.getBody())));
        assertThat(response.getBody().readableBytes()).isEqualTo(0);
        response.getBody().release();
    }

//    @Test
//    void executeBasicAuthentication() {
//        HttpConnection connection = new HttpConnection(ConnectionConfig.builder().from(config)
//                .authenticationMethod(AuthenticationMethod.ofBasic("user", "password"))
//                .build());
//
//        ArangoResponse response = connection.execute(request).block();
//
//        // authorization
//        assertThat(response).isNotNull();
//        assertThat(response.getMeta()).containsKey("authorization");
//        assertThat(response.getMeta().get("authorization")).isEqualTo("Basic dXNlcjpwYXNzd29yZA==");
//
//        response.getBody().release();
//    }

}