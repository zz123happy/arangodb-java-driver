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

import com.arangodb.next.connection.vst.RequestType;
import com.arangodb.velocypack.VPackBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.ValueType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import reactor.core.publisher.Flux;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class ConnectionTest {

    private static final String JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjEuNTczMjE3ODk4MjM1NTAzNGUrNiwiZXhwIjoxNTc1ODA5ODk4LCJpc3MiOiJhcmFuZ29kYiIsInByZWZlcnJlZF91c2VybmFtZSI6InJvb3QifQ==.SYc7_Vffkmd8zajJ_7_9DZG0GUcolXdgqtEAX0M0ECQ=";

    static private Stream<Arguments> protocolAndAuthenticationMethodProvider() {
        return Stream.of(
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofBasic("root", "test")),
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofJwt(JWT)),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofBasic("root", "test")),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofJwt(JWT))
        );
    }

    private ConnectionConfig config = ConnectionConfig.builder()
            .host(HostDescription.of("172.28.3.1", 8529))
            .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
            .build();

    private ArangoRequest getRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/version")
            .requestType(RequestType.GET)
            .putQueryParam("details", "true")
            .build();

    private ArangoRequest postRequest = ArangoRequest.builder()
            .database("_system")
            .path("/_api/query")
            .requestType(RequestType.POST)
            .body(VPackUtils.extractBuffer(createParseQueryRequestBody()))
            .build();

    @ParameterizedTest
    @MethodSource("protocolAndAuthenticationMethodProvider")
    void getRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ConnectionConfig testConfig = ConnectionConfig.builder()
                .from(config)
                .authenticationMethod(authenticationMethod)
                .build();

        ArangoResponse response = ArangoConnection.create(protocol, testConfig)
                .flatMap(connection -> connection.execute(getRequest))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);
        System.out.println(response);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");
        System.out.println(responseBodySlice);

        response.getBody().release();
    }

    @ParameterizedTest
    @MethodSource("protocolAndAuthenticationMethodProvider")
    void postRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ConnectionConfig testConfig = ConnectionConfig.builder()
                .from(config)
                .authenticationMethod(authenticationMethod)
                .build();

        ArangoResponse response = ArangoConnection.create(protocol, testConfig)
                .flatMap(connection -> connection.execute(postRequest))
                .block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);
        System.out.println(response);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("parsed").getAsBoolean()).isEqualTo(true);
        System.out.println(responseBodySlice);

        response.getBody().release();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void parallelLoop(ArangoProtocol protocol) {
        ArangoConnection.create(protocol, config).flatMapMany(connection -> Flux.range(0, 1000)
                .flatMap(i -> connection.execute(getRequest))
                .doOnNext(v -> {
                    new VPackSlice(IOUtilsTest.getByteArray(v.getBody()));
                    v.getBody().release();
                }))
                .then().block();
    }

    @Test
    @Disabled
    void inifiniteParallelLoop() {
        ArangoConnection.create(ArangoProtocol.VST, config).flatMapMany(connection -> Flux.fromStream(Stream.iterate(0, i -> i + 1))
                .flatMap(i -> connection.execute(getRequest))
                .doOnNext(v -> {
                    new VPackSlice(IOUtilsTest.getByteArray(v.getBody()));
                    v.getBody().release();
                }))
                .then().block();
    }

    private VPackSlice createParseQueryRequestBody() {
        final VPackBuilder builder = new VPackBuilder();
        builder.add(ValueType.OBJECT);
        builder.add("query", "FOR i IN 1..100 RETURN i * 3");
        builder.close();
        return builder.slice();
    }

}