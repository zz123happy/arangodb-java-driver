/*
 * DISCLAIMER
 *
 * Copyright 2017 ArangoDB GmbH, Cologne, Germany
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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.netty.ByteBufMono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.Map.Entry;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.handler.codec.http.HttpHeaderNames.*;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */

final public class HttpConnection implements ArangoConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnection.class);
    static final String THREAD_PREFIX = "arango-http";
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_VPACK = "application/x-velocypack";

    private final Scheduler scheduler;
    private final ConnectionConfig config;
    private final ConnectionProvider connectionProvider;
    private final HttpClient client;
    private final CookieStore cookieStore;

    public HttpConnection(final ConnectionConfig config) {
        this.config = config;
        scheduler = Schedulers.newSingle(THREAD_PREFIX);
        connectionProvider = createConnectionProvider();
        client = getClient();
        cookieStore = new CookieStore();
    }

    @Override
    public Mono<ArangoConnection> initialize() {
        return Mono.just(this);
    }

    @Override
    public Mono<ArangoResponse> execute(final ArangoRequest request) {
        final String url = buildUrl(request);
        return runOnScheduler(() ->
                createHttpClient(request, request.getBody().readableBytes())
                        .request(requestTypeToHttpMethod(request.getRequestType())).uri(url)
                        .send(Mono.just(request.getBody()))
                        .responseSingle(this::buildResponse))
                .doOnError(e -> close().subscribe())
                .timeout(Duration.ofMillis(config.getTimeout()));
    }

    @Override
    public Mono<Void> close() {
        return connectionProvider.disposeLater()
                .publishOn(scheduler)
                .doOnTerminate(cookieStore::clear);
    }

    private ConnectionProvider createConnectionProvider() {
        return ConnectionProvider.fixed(
                "http",
                1,  // FIXME: connection pooling should happen here, inside HttpConnection
                config.getTimeout(),
                config.getTtl());
    }

    private HttpClient getClient() {
        return applySslContext(
                HttpClient
                        .create(connectionProvider)
                        .tcpConfiguration(tcpClient -> tcpClient.option(CONNECT_TIMEOUT_MILLIS, config.getTimeout()))
                        .wiretap(true)
                        .protocol(HttpProtocol.HTTP11)
                        .keepAlive(true)
                        .baseUrl((Boolean.TRUE == config.getUseSsl() ? "https://" : "http://") + config.getHost().getHost() + ":" + config.getHost().getPort())
                        .headers(headers -> config.getAuthenticationMethod().ifPresent(
                                method -> headers.set(AUTHORIZATION, method.getHttpAuthorizationHeader())
                        ))
        );
    }

    private HttpClient applySslContext(HttpClient httpClient) {
        if (config.getUseSsl() && config.getSslContext().isPresent()) {
            return httpClient.secure(spec ->
                    spec.sslContext(new JdkSslContext(config.getSslContext().get(), true, ClientAuth.NONE)));
        } else {
            return httpClient;
        }
    }

    private static String buildUrl(final ArangoRequest request) {
        final StringBuilder sb = new StringBuilder();
        final String database = request.getDatabase();
        if (database != null && !database.isEmpty()) {
            sb.append("/_db/").append(database);
        }
        sb.append(request.getPath());

        if (!request.getQueryParam().isEmpty()) {
            sb.append("?");
            final String paramString = request.getQueryParam().entrySet().stream()
                    .map(it -> it.getKey() + "=" + it.getValue())
                    .collect(Collectors.joining("&"));
            sb.append(paramString);
        }
        return sb.toString();
    }

    private HttpMethod requestTypeToHttpMethod(final ArangoRequest.RequestType requestType) {
        switch (requestType) {
            case POST:
                return HttpMethod.POST;
            case PUT:
                return HttpMethod.PUT;
            case PATCH:
                return HttpMethod.PATCH;
            case DELETE:
                return HttpMethod.DELETE;
            case HEAD:
                return HttpMethod.HEAD;
            case GET:
                return HttpMethod.GET;
            default:
                throw new IllegalArgumentException();
        }
    }

    private String getContentType() {
        switch (config.getContentType()) {
            case VPACK:
                return CONTENT_TYPE_VPACK;
            case JSON:
                return CONTENT_TYPE_APPLICATION_JSON;
            default:
                throw new IllegalArgumentException();
        }
    }

    private HttpClient createHttpClient(final ArangoRequest request, final int bodyLength) {
        return cookieStore.addCookies(client)
                .headers(headers -> {
                    headers.set(CONTENT_LENGTH, bodyLength);
                    if (config.getContentType() == ContentType.VPACK) {
                        headers.set(ACCEPT, "application/x-velocypack");
                    } else if (config.getContentType() == ContentType.JSON) {
                        headers.set(ACCEPT, "application/json");
                    } else {
                        throw new IllegalArgumentException();
                    }
                    addHeaders(request, headers);
                    if (bodyLength > 0) {
                        headers.set(CONTENT_TYPE, getContentType());
                    }
                });
    }

    /**
     * Executes the provided task in the scheduler.
     *
     * @param task task to execute
     * @param <T>  type returned
     * @return the supplied mono
     */
    private <T> Mono<T> runOnScheduler(Supplier<Mono<T>> task) {
        return Mono.defer(task).subscribeOn(scheduler);
    }

    private static void addHeaders(final ArangoRequest request, final HttpHeaders headers) {
        for (final Entry<String, String> header : request.getHeaderParam().entrySet()) {
            headers.add(header.getKey(), header.getValue());
        }
    }

    private Mono<ArangoResponse> buildResponse(HttpClientResponse resp, ByteBufMono bytes) {
        return bytes
                .switchIfEmpty(Mono.just(Unpooled.EMPTY_BUFFER))
                .map(byteBuf -> (ArangoResponse) ArangoResponse.builder()
                        .responseCode(resp.status().code())
                        .putAllMeta(resp.responseHeaders().entries().stream().collect(Collectors.toMap(Entry::getKey, Entry::getValue)))
                        .body(IOUtils.copyOf(byteBuf))
                        .build())
                .publishOn(scheduler)
                .doOnNext(it -> {
                    if (config.getResendCookies()) {
                        cookieStore.saveCookies(resp);
                    }
                });
    }

}
