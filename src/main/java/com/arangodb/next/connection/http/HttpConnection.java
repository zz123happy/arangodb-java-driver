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
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.next.connection.vst.RequestType;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.ssl.*;
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

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS;
import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static reactor.netty.resources.ConnectionProvider.DEFAULT_POOL_ACQUIRE_TIMEOUT;

/**
 * @author Mark Vollmary
 */
public class HttpConnection implements ArangoConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpConnection.class);
    private static final String CONTENT_TYPE_APPLICATION_JSON = "application/json; charset=UTF-8";
    private static final String CONTENT_TYPE_VPACK = "application/x-velocypack";

    public static class Builder {
        private String user;
        private String password;
        private Boolean useSsl;
        private Boolean resendCookies;
        private ContentType contentType;
        private HostDescription host;
        private Long ttl;
        private SSLContext sslContext;
        private Integer timeout;

        public Builder user(final String user) {
            this.user = user;
            return this;
        }

        public Builder password(final String password) {
            this.password = password;
            return this;
        }

        public Builder useSsl(final Boolean useSsl) {
            this.useSsl = useSsl;
            return this;
        }

        public Builder httpResendCookies(Boolean resendCookies) {
            this.resendCookies = resendCookies;
            return this;
        }

        public Builder contentType(final ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder host(final HostDescription host) {
            this.host = host;
            return this;
        }

        public Builder ttl(final Long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder sslContext(final SSLContext sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder timeout(final Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public HttpConnection build() {
            return new HttpConnection(host, timeout, user, password, useSsl, sslContext, contentType, ttl, resendCookies);
        }
    }

    private final String user;
    private final String password;
    private final Boolean useSsl;
    private final SSLContext sslContext;
    private final ContentType contentType;
    private final HostDescription host;
    private final Integer timeout;
    private final ConnectionProvider connectionProvider;
    private final HttpClient client;
    private final Long ttl;
    private final Boolean resendCookies;

    private final Scheduler scheduler;
    private final Map<Cookie, Long> cookies;

    private HttpConnection(final HostDescription host, final Integer timeout, final String user, final String password,
                           final Boolean useSsl, final SSLContext sslContext, final ContentType contentType,
                           final Long ttl, final Boolean resendCookies) {
        super();
        this.host = host;
        this.timeout = timeout;
        this.user = user;
        this.password = password;
        this.useSsl = useSsl;
        this.sslContext = sslContext;
        this.contentType = contentType;
        this.ttl = ttl;
        this.resendCookies = resendCookies;

        this.scheduler = Schedulers.single();
        this.cookies = new HashMap<>();

        this.connectionProvider = initConnectionProvider();
        this.client = initClient();
    }

    private ConnectionProvider initConnectionProvider() {
        return ConnectionProvider.fixed(
                "http",
                1,  // FIXME: connection pooling should happen here, inside HttpConnection
                getAcquireTimeout(),
                ttl != null ? Duration.ofMillis(ttl) : null);
    }

    private long getAcquireTimeout() {
        return timeout != null && timeout >= 0 ? timeout : DEFAULT_POOL_ACQUIRE_TIMEOUT;
    }

    private HttpClient initClient() {
        return applySslContext(
                HttpClient
                        .create(connectionProvider)
                        .tcpConfiguration(tcpClient ->
                                timeout != null && timeout >= 0 ? tcpClient.option(CONNECT_TIMEOUT_MILLIS, timeout) : tcpClient)
                        .wiretap(true)
                        .protocol(HttpProtocol.HTTP11)
                        .keepAlive(true)
                        .baseUrl((Boolean.TRUE == useSsl ? "https://" : "http://") + host.getHost() + ":" + host.getPort())
                        .headers(headers -> {
                            if (user != null)
                                headers.set(AUTHORIZATION, buildBasicAuthentication(user, password));
                        })
        );
    }

    private HttpClient applySslContext(HttpClient httpClient) {
        if (Boolean.TRUE == useSsl && sslContext != null) {
            //noinspection deprecation
            return httpClient.secure(spec -> spec.sslContext(new JdkSslContext(sslContext, true, ClientAuth.NONE)));
        } else {
            return httpClient;
        }
    }

    private static String buildBasicAuthentication(final String principal, final String password) {
        final String plainAuth = principal + ":" + (password == null ? "" : password);
        String encodedAuth = Base64.getEncoder().encodeToString(plainAuth.getBytes());
        return "Basic " + encodedAuth;
    }

    @Override
    public void close() {
        connectionProvider.disposeLater().block();
    }

    private static String buildUrl(final Request request) {
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
        if (contentType == ContentType.VPACK) {
            return CONTENT_TYPE_VPACK;
        } else if (contentType == ContentType.JSON) {
            return CONTENT_TYPE_APPLICATION_JSON;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private HttpClient createHttpClient(final Request request, final int bodyLength) {
        return addCookies(client)
                .headers(headers -> {
                    headers.set(CONTENT_LENGTH, bodyLength);
                    headers.set(USER_AGENT, "Mozilla/5.0 (compatible; ArangoDB-JavaDriver/1.1; +http://mt.orz.at/)");
                    if (contentType == ContentType.VPACK) {
                        headers.set(ACCEPT, "application/x-velocypack");
                    }
                    addHeaders(request, headers);
                    if (bodyLength > 0) {
                        headers.set(CONTENT_TYPE, getContentType());
                    }
                });
    }

    @Override
    public Mono<Response> execute(final Request request) {
        final String url = buildUrl(request);

        return applyTimeout(
                Mono.defer(() ->
                        // this block runs on the single scheduler executor, so that cookies reads and writes are
                        // always performed by the same thread, thus w/o need for concurrency management
                        createHttpClient(request, request.getBody().length)
                                .request(requestTypeToHttpMethod(request.getRequestType())).uri(url)
                                .send(Mono.just(Unpooled.wrappedBuffer(request.getBody())))
                                .responseSingle(this::buildResponse))
                        .subscribeOn(scheduler)
        );
    }

    private Mono<Response> applyTimeout(Mono<Response> client) {
        if (timeout != null && timeout > 0) {
            return client.timeout(Duration.ofMillis(timeout));
        } else {
            return client;
        }
    }

    private static void addHeaders(final Request request, final HttpHeaders headers) {
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
        if (resendCookies != null && resendCookies) {
            resp.cookies().values().stream().flatMap(Collection::stream)
                    .forEach(cookie -> {
                        LOGGER.debug("saving cookie: {}", cookie);
                        cookies.put(cookie, new Date().getTime());
                    });
        }
    }

    private Mono<Response> buildResponse(HttpClientResponse resp, ByteBufMono bytes) {

        final Mono<VPackSlice> vPackSliceMono;

        if (resp.method() == HttpMethod.HEAD || "0".equals(resp.responseHeaders().get(CONTENT_LENGTH))) {
            vPackSliceMono = Mono.just(new VPackSlice(null));
        } else if (contentType == ContentType.VPACK) {
            vPackSliceMono = bytes.asByteArray().map(VPackSlice::new);
        } else if (contentType == ContentType.JSON) {
            vPackSliceMono = bytes.asInputStream()
                    // FIXME
                    .map(input -> null);
        } else {
            throw new IllegalArgumentException();
        }

        return vPackSliceMono
                .map(body -> {
                    final Response response = new Response();
                    response.setResponseCode(resp.status().code());
                    resp.responseHeaders().forEach(it -> response.getMeta().put(it.getKey(), it.getValue()));
                    if (body.getBuffer() != null && body.getBuffer().length > 0) {
                        response.setBody(body);
                    }
                    return response;
                })
                .publishOn(scheduler)
                .doOnNext(it -> saveCookies(resp));
    }

}
