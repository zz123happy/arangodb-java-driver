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

import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import java.time.Duration;
import java.util.Optional;

import static com.arangodb.next.ArangoDefaults.*;

/**
 * @author Michele Rastelli
 */
@Value.Immutable
@SuppressWarnings("SameReturnValue")
public interface ConnectionConfig {

    static ImmutableConnectionConfig.Builder builder() {
        return ImmutableConnectionConfig.builder();
    }

    /**
     * @return max number of connections, used by HttpConnection only
     */
    @Value.Default
    default int getMaxConnections() {
        return 10;
    }

    /**
     * @return use SSL connection
     */
    @Value.Default
    default boolean getUseSsl() {
        return false;
    }

    Optional<SSLContext> getSslContext();

    @Value.Default
    default ContentType getContentType() {
        return ContentType.VPACK;
    }

    /**
     * @return connect, request and pool acquisition timeout timeout (millisecond)
     */
    @Value.Default
    default int getTimeout() {
        return (int) DEFAULT_TIMEOUT;
    }

    /**
     * @return the {@link Duration} after which the channel will be closed (resolution: ms), if {@code null} there is no
     * max idle time
     */
    @Nullable
    Duration getTtl();

    /**
     * @return VelocyStream Chunk content-size (bytes), used by VstConnection only
     */
    @Value.Default
    default int getChunkSize() {
        return CHUNK_DEFAULT_CONTENT_SIZE;
    }

    /**
     * @return whether the connection should resend the received cookies and honour the related maxAge, used by
     * HttpConnection only
     */
    @Value.Default
    default boolean getResendCookies() {
        return true;
    }

}
