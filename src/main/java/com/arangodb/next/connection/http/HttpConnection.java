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
import com.arangodb.next.connection.vst.RequestType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cookie.Cookie;
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
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.handler.codec.http.HttpHeaderNames.*;

/**
 * @author Mark Vollmary
 */

public class HttpConnection implements ArangoConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnection.class);
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_VPACK = "application/x-velocypack";

    private final Scheduler scheduler = Schedulers.single();
    private final Map<Cookie, Long> cookies = new HashMap<>();

    private final ConnectionConfig config;
    private ConnectionProvider connectionProvider;
    private final HttpClient client;

    HttpConnection(final ConnectionConfig config) {
        this.config = config;
        connectionProvider = getConnectionProvider();
        client = getClient();
    }

    private ConnectionProvider getConnectionProvider() {
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

    @Override
    public Mono<Void> close() {
        return connectionProvider.disposeLater();
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

    private HttpMethod requestTypeToHttpMethod(final RequestType requestType) {
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
        return addCookies(client)
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

    @Override
    public Mono<ArangoResponse> execute(final ArangoRequest request) {
        final String url = buildUrl(request);
        return Mono.defer(() ->
                // this block runs on the single scheduler executor, so that cookies reads and writes are
                // always performed by the same thread, thus w/o need for concurrency management
                createHttpClient(request, request.getBody().readableBytes())
                        .request(requestTypeToHttpMethod(request.getRequestType())).uri(url)
                        .send(Mono.just(request.getBody()))
                        .responseSingle(this::buildResponse))
                .subscribeOn(scheduler)
                .timeout(Duration.ofMillis(config.getTimeout()));
    }


    private static void addHeaders(final ArangoRequest request, final HttpHeaders headers) {
        for (final Entry<String, String> header : request.getHeaderParam().entrySet()) {
            headers.add(header.getKey(), header.getValue());
        }
    }

    private void removeExpiredCookies() {
        long now = new Date().getTime();
        boolean removed = cookies.entrySet().removeIf(entry -> entry.getKey().maxAge() >= 0 && entry.getValue() + entry.getKey().maxAge() * 1000 < now);
        if (removed) {
            LOGGER.debug("removed cookies");
        }
    }

    private HttpClient addCookies(final HttpClient httpClient) {
        removeExpiredCookies();
        HttpClient c = httpClient;
        for (Cookie cookie : cookies.keySet()) {
            LOGGER.debug("sending cookie: {}", cookie);
            c = c.cookie(cookie);
        }
        return c;
    }

    private void saveCookies(HttpClientResponse resp) {
        if (config.getResendCookies()) {
            resp.cookies().values().stream().flatMap(Collection::stream)
                    .forEach(cookie -> {
                        LOGGER.debug("saving cookie: {}", cookie);
                        cookies.put(cookie, new Date().getTime());
                    });
        }
    }

    private Mono<ArangoResponse> buildResponse(HttpClientResponse resp, ByteBufMono bytes) {
        return bytes
                .switchIfEmpty(Mono.just(Unpooled.EMPTY_BUFFER))
                .map(byteBuf -> {
                    final ArangoResponse response = new ArangoResponse();
                    response.setResponseCode(resp.status().code());
                    resp.responseHeaders().forEach(it -> response.getMeta().put(it.getKey(), it.getValue()));
                    response.setBody(IOUtils.copyOf(byteBuf));
                    return response;
                })
                .publishOn(scheduler)
                .doOnNext(it -> saveCookies(resp));
    }

}
