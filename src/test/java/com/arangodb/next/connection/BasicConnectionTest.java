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

import com.arangodb.next.connection.exceptions.ArangoConnectionAuthenticationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import deployments.ContainerDeployment;
import io.netty.buffer.Unpooled;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.security.KeyStore;
import java.util.stream.Stream;

import static com.arangodb.next.connection.ConnectionTestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Michele Rastelli
 */
@Testcontainers
class BasicConnectionTest {

    private static final String SSL_TRUSTSTORE = "/example.truststore";
    private static final String SSL_TRUSTSTORE_PASSWORD = "12345678";

    private static HostDescription host;
    private static String jwt;

    private final ConnectionConfig config;

    BasicConnectionTest() throws Exception {
        config = ConnectionConfig.builder()
                .useSsl(true)
                .sslContext(getSslContext())
                .build();
    }

    /**
     * Provided arguments are:
     * - ArangoProtocol
     * - AuthenticationMethod
     */
    static private Stream<Arguments> argumentsProvider() {
        return Stream.of(
                Arguments.of(ArangoProtocol.VST, deployment.getAuthentication()),
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofJwt(jwt)),
                Arguments.of(ArangoProtocol.HTTP, deployment.getAuthentication()),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofJwt(jwt))
        );
    }

    /**
     * Provided arguments are:
     * - ArangoProtocol
     * - AuthenticationMethod
     */
    static private Stream<Arguments> wrongAuthenticationArgumentsProvider() {
        return Stream.of(
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofBasic(deployment.getUser(), "wrong")),
                Arguments.of(ArangoProtocol.VST, AuthenticationMethod.ofJwt("invalid.jwt.token")),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofBasic(deployment.getUser(), "wrong")),
                Arguments.of(ArangoProtocol.HTTP, AuthenticationMethod.ofJwt("invalid.jwt.token"))
        );
    }

    @Container
    private static final ContainerDeployment deployment = ContainerDeployment.ofSingleServerWithSsl();

    @BeforeAll
    static void setup() throws Exception {
        host = deployment.getHosts().get(0);
        SslContext sslContext = getSslContext();

        String request = "{\"username\":\"" + deployment.getUser() + "\",\"password\":\"" + deployment.getPassword() + "\"}";
        String response = HttpClient.create()
                .tcpConfiguration(tcp -> tcp.secure(c -> c.sslContext(sslContext)))
                .post()
                .uri("https://" + host.getHost() + ":" + host.getPort() + "/_db/_system/_open/auth")
                .send(Mono.just(Unpooled.wrappedBuffer(request.getBytes())))
                .responseContent()
                .asString()
                .blockFirst();
        jwt = new ObjectMapper().readTree(response).get("jwt").asText();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void getRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ArangoConnection connection = new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY)
                .create(host, authenticationMethod).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(ConnectionTestUtils.VERSION_REQUEST).block();
        verifyGetResponseVPack(response);
        connection.close().block();
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void postRequest(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        ArangoConnection connection = new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY)
                .create(host, authenticationMethod).block();
        assertThat(connection).isNotNull();
        ArangoResponse response = connection.execute(ConnectionTestUtils.postRequest()).block();
        verifyPostResponseVPack(response);
        connection.close().block();
    }

    @ParameterizedTest
    @EnumSource(ArangoProtocol.class)
    void parallelLoop(ArangoProtocol protocol) {
        new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY).create(host, deployment.getAuthentication())
                .flatMapMany(c ->
                        Flux.range(0, 1_000)
                                .flatMap(i -> c.execute(ConnectionTestUtils.VERSION_REQUEST))
                                .doOnNext(response -> {
                                    assertThat(response).isNotNull();
                                    assertThat(response.getVersion()).isEqualTo(1);
                                    assertThat(response.getType()).isEqualTo(2);
                                    assertThat(response.getResponseCode()).isEqualTo(200);
                                    verifyGetResponseVPack(response);
                                }))
                .then().block();
    }

    @ParameterizedTest
    @MethodSource("wrongAuthenticationArgumentsProvider")
    void authenticationFailure(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        assertThrows(ArangoConnectionAuthenticationException.class, () ->
                new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY).create(host, authenticationMethod)
                        .block()
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void wrongHostFailure(ArangoProtocol protocol, AuthenticationMethod authenticationMethod) {
        HostDescription wrongHost = HostDescription.of("wrongHost", 8529);
        Throwable thrown = catchThrowable(() -> new ConnectionFactoryImpl(config, protocol, DEFAULT_SCHEDULER_FACTORY).create(wrongHost, authenticationMethod)
                .block());
        assertThat(Exceptions.unwrap(thrown)).isInstanceOf(IOException.class);
    }

    private static SslContext getSslContext() throws Exception {
        final KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(SslTls13ConnectionTest.class.getResourceAsStream(SSL_TRUSTSTORE), SSL_TRUSTSTORE_PASSWORD.toCharArray());

        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, SSL_TRUSTSTORE_PASSWORD.toCharArray());

        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        return SslContextBuilder
                .forClient()
                .sslProvider(SslProvider.JDK)
                .trustManager(tmf)
                .build();
    }

}