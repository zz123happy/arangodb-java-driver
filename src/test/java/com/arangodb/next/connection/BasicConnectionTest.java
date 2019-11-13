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

import com.arangodb.velocypack.VPackBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.ValueType;
import com.fasterxml.jackson.databind.ObjectMapper;
import containers.SingleServerWithSmallChunkSizeContainer;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class BasicConnectionTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BasicConnectionTest.class);
    private static HostDescription host;
    private static String jwt;
    private final ImmutableConnectionConfig.Builder config;
    private final ArangoRequest getRequest;
    private final ArangoRequest postRequest;

    BasicConnectionTest() {
        config = ConnectionConfig.builder()
                .authenticationMethod(AuthenticationMethod.ofBasic("root", "test"))
                .chunkSize(8);

        getRequest = ArangoRequest.builder()
                .database("_system")
                .path("/_api/version")
                .requestType(ArangoRequest.RequestType.GET)
                .putQueryParam("details", "true")
                .build();

        postRequest = ArangoRequest.builder()
                .database("_system")
                .path("/_api/query")
                .requestType(ArangoRequest.RequestType.POST)
                .body(VPackUtils.extractBuffer(createParseQueryRequestBody()))
                .build();
    }

    /**
     * Provided arguments are:
     * - ArangoProtocol
     * - AuthenticationMethod
     */
    static private Stream<Arguments> argumentsProvider() {
        return Stream.of(
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofBasic("root", "test")),
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofJwt(jwt)),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofBasic("root", "test")),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofJwt(jwt))
        );
    }

    @BeforeAll
    static void setup() throws IOException {
        host = SingleServerWithSmallChunkSizeContainer.INSTANCE.start().join().getHostDescription();
        String request = "{\"username\":\"root\",\"password\":\"test\"}";
        String response = HttpClient.create()
                .post()
                .uri("http://" + host.getHost() + ":" + host.getPort() + "/_db/_system/_open/auth")
                .send(Mono.just(Unpooled.wrappedBuffer(request.getBytes())))
                .responseContent()
                .asString()
                .blockFirst();
        jwt = new ObjectMapper().readTree(response).get("jwt").asText();
    }

    @AfterAll
    static void shutDown() {
        SingleServerWithSmallChunkSizeContainer.INSTANCE.stop().join();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void getRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ConnectionConfig testConfig = config
                .host(host)
                .authenticationMethod(authenticationMethod)
                .build();

        ArangoConnection connection = ArangoConnection.create(protocol, testConfig).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(getRequest).block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);
        System.out.println(response);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");
        System.out.println(responseBodySlice);

        response.getBody().release();
        connection.close().block();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void postRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ConnectionConfig testConfig = config
                .host(host)
                .authenticationMethod(authenticationMethod)
                .build();

        ArangoConnection connection = ArangoConnection.create(protocol, testConfig).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(postRequest).block();

        assertThat(response).isNotNull();
        assertThat(response.getVersion()).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(2);
        assertThat(response.getResponseCode()).isEqualTo(200);
        System.out.println(response);

        VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
        assertThat(responseBodySlice.get("parsed").getAsBoolean()).isEqualTo(true);
        System.out.println(responseBodySlice);

        response.getBody().release();
        connection.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void parallelLoop(ArangoProtocol protocol) {
        ArangoConnection.create(protocol, config.host(host).build()).flatMapMany(connection -> Flux.range(0, 1_000)
                .flatMap(i -> connection.execute(getRequest))
                .doOnNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.getVersion()).isEqualTo(1);
                    assertThat(response.getType()).isEqualTo(2);
                    assertThat(response.getResponseCode()).isEqualTo(200);

                    VPackSlice responseBodySlice = new VPackSlice(IOUtilsTest.getByteArray(response.getBody()));
                    assertThat(responseBodySlice.get("server").getAsString()).isEqualTo("arango");

                    response.getBody().release();
                }))
                .then().block();
    }

    @Test
    @Disabled
    void inifiniteParallelLoop() {
        ArangoConnection.create(ArangoProtocol.VST, config.host(host).build()).flatMapMany(connection -> Flux.fromStream(Stream.iterate(0, i -> i + 1))
                .flatMap(i -> connection.execute(getRequest))
                .doOnNext(v -> {
                    new VPackSlice(IOUtilsTest.getByteArray(v.getBody()));
                    v.getBody().release();
                }))
                .onErrorContinue((throwable, o) -> LOGGER.info(throwable.getClass().getSimpleName() + " while processing: {}", o))
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