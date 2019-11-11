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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.immutables.value.Value;

import java.util.Map;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 * @see <a href="https://github.com/arangodb/velocystream#request--response">API
 */
@Value.Immutable
@SuppressWarnings("SameReturnValue")
public interface ArangoRequest {

    static ImmutableArangoRequest.Builder builder() {
        return ImmutableArangoRequest.builder();
    }

    default int getVersion() {
        return 1;
    }

    default int getType() {
        return 1;
    }

    String getDatabase();

    RequestType getRequestType();

    String getPath();

    Map<String, String> getQueryParam();

    Map<String, String> getHeaderParam();

    // TODO: refactor to Publisher<ByteBuf>
    @Value.Default
    default ByteBuf getBody() {
        return Unpooled.EMPTY_BUFFER;
    }


    enum RequestType {
        DELETE(0),
        GET(1),
        POST(2),
        PUT(3),
        HEAD(4),
        PATCH(5),
        OPTIONS(6),
        VSTREAM_CRED(7),
        VSTREAM_REGISTER(8),
        VSTREAM_STATUS(9),
        ILLEGAL(10);

        private final int type;

        RequestType(final int type) {
            this.type = type;
        }

        public int getType() {
            return type;
        }

    }

}
