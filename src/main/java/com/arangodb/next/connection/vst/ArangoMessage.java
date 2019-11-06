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

import com.arangodb.next.connection.ArangoRequest;
import com.arangodb.next.connection.ArangoResponse;
import com.arangodb.next.connection.IOUtils;
import com.arangodb.velocypack.VPackBuilder;
import com.arangodb.velocypack.VPackSlice;
import com.arangodb.velocypack.ValueType;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.List;

import static com.arangodb.next.ArangoDefaults.HEADER_SIZE;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 */
class ArangoMessage {

    private final long id;
    private final ByteBuf payload;

    static ArangoResponse fromBuffer(byte[] buffer) {
        VPackSlice head = new VPackSlice(buffer);
        ArangoResponse response = deserializeArangoResponseHead(head);
        final int headSize = head.getByteSize();
        ByteBuf body = IOUtils.createBuffer(buffer.length - headSize);
        body.writeBytes(buffer, headSize, buffer.length - headSize);
        response.setBody(body);
        return response;
    }

    private static ArangoMessage fromRequest(long id, ArangoRequest request) {
        VPackSlice headSlice = serializeArangoRequestHead(request);
        int headSize = headSlice.getByteSize();
        ByteBuf payload = IOUtils.createBuffer(headSize + request.getBody().readableBytes());
        payload.writeBytes(headSlice.getBuffer(), 0, headSize);
        payload.writeBytes(request.getBody());
        request.getBody().release();
        return new ArangoMessage(id, payload);
    }

    private ArangoMessage(final long id, final ByteBuf payload) {
        super();
        this.id = id;
        this.payload = payload;
    }

    private long getId() {
        return id;
    }

    private ByteBuf getPayload() {
        return payload;
    }

    static ByteBuf writeChunked(long id, ArangoRequest request, int chunkSize) {
        ArangoMessage message = fromRequest(id, request);
        return message.writeChunked(chunkSize);
    }

    private ByteBuf writeChunked(int chunkSize) {
        final ByteBuf out = IOUtils.createBuffer();

        for (final Chunk chunk : buildChunks(chunkSize)) {

            final int length = chunk.getContentLength() + HEADER_SIZE;

            out.writeIntLE(length);
            out.writeIntLE(chunk.getChunkX());
            out.writeLongLE(chunk.getMessageId());
            out.writeLongLE(chunk.getMessageLength());

            final int contentOffset = chunk.getContentOffset();
            final int contentLength = chunk.getContentLength();

            out.writeBytes(payload, contentOffset, contentLength);
        }

        payload.release();
        return out;
    }

    private List<Chunk> buildChunks(int chunkSize) {
        final List<Chunk> chunks = new ArrayList<>();
        int size = payload.readableBytes();
        final int totalSize = size;
        final int n = size / chunkSize;
        final int numberOfChunks = (size % chunkSize != 0) ? (n + 1) : n;
        int off = 0;
        for (int i = 0; size > 0; i++) {
            final int len = Math.min(chunkSize, size);
            final Chunk chunk = new Chunk(id, i, numberOfChunks, totalSize, off, len);
            size -= len;
            off += len;
            chunks.add(chunk);
        }
        return chunks;
    }

    private static VPackSlice serializeArangoRequestHead(ArangoRequest request) {
        final VPackBuilder builder = new VPackBuilder();
        builder.add(ValueType.ARRAY);
        builder.add(request.getVersion());
        builder.add(request.getType());
        builder.add(request.getDatabase());
        builder.add(request.getRequestType().getType());
        builder.add(request.getPath());
        builder.add(ValueType.OBJECT);
        request.getQueryParam().forEach(builder::add);
        builder.close();
        builder.add(ValueType.OBJECT);
        request.getHeaderParam().forEach(builder::add);
        builder.close();
        builder.close();
        return builder.slice();
    }

    private static ArangoResponse deserializeArangoResponseHead(VPackSlice vpack){
        final ArangoResponse response = new ArangoResponse();
        response.setVersion(vpack.get(0).getAsInt());
        response.setType(vpack.get(1).getAsInt());
        response.setResponseCode(vpack.get(2).getAsInt());
        if (vpack.size() > 3) {
//            final Map<String, Object> map = new VPack.Builder().build().deserialize(builder.slice(), Map.class);
//            response.setMeta(context.deserialize(vpack.get(3), Map.class));
        }
        return response;
    }
}
