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

import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.connection.IOUtils;
import com.arangodb.next.connection.ImmutableArangoResponse;
import com.arangodb.velocypack.VPackSlice;
import io.netty.buffer.ByteBuf;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
final class ResponseConverter {

    /**
     * @param buffer received VST buffer
     * @return ArangoDB response
     */
    static ArangoResponse decodeResponse(byte[] buffer) {
        VPackSlice head = new VPackSlice(buffer);
        final int headSize = head.getByteSize();
        ByteBuf body = IOUtils.createBuffer(buffer.length - headSize);
        body.writeBytes(buffer, headSize, buffer.length - headSize);
        return buildArangoResponse(head, body);
    }

    private static ArangoResponse buildArangoResponse(VPackSlice vpack, ByteBuf body) {
        ImmutableArangoResponse.Builder builder = ArangoResponse.builder()
                .body(body)
                .version(vpack.get(0).getAsInt())
                .type(vpack.get(1).getAsInt())
                .responseCode(vpack.get(2).getAsInt());

        if (vpack.size() > 3) {
            Iterator<Map.Entry<String, VPackSlice>> metaIterator = vpack.get(3).objectIterator();
            while (metaIterator.hasNext()) {
                Map.Entry<String, VPackSlice> meta = metaIterator.next();
                builder.putMeta(meta.getKey(), meta.getValue().getAsString());
            }
        }

        return builder.build();
    }
}
