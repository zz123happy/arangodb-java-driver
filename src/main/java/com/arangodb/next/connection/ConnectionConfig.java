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


import org.immutables.value.Value;

import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Optional;

import static reactor.netty.resources.ConnectionProvider.DEFAULT_POOL_ACQUIRE_TIMEOUT;

/**
 * @author Michele Rastelli
 */
@Value.Immutable
public interface ConnectionConfig {

    Optional<String> getUser();

    @Value.Default
    default String getPassword() {
        return "";
    }

    @Value.Default
    default boolean getUseSsl() {
        return false;
    }

    Optional<SSLContext> getSslContext();

    ContentType getContentType();

    HostDescription getHost();

    /**
     * @return connection timeout in ms
     */
    @Value.Default
    default int getTimeout() {
        return (int) DEFAULT_POOL_ACQUIRE_TIMEOUT;
    }

    @Value.Default
    default Duration getTtl() {
        return Duration.ofSeconds(30);
    }

    @Value.Default
    default boolean getResendCookies() {
        return true;
    }

}
