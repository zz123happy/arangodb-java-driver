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

package com.arangodb.next.entity.serde;

import com.arangodb.next.connection.ContentType;
import com.arangodb.velocypack.VPack;
import com.arangodb.velocypack.VPackSlice;

import java.lang.reflect.Type;

/**
 * @author Michele Rastelli
 */
public abstract class ArangoSerde {

    private final VPack vPack = new VPack.Builder()
            .registerModule(new VPackDriverModule())
            .build();

    public static ArangoSerde of(final ContentType contentType) {
        switch (contentType) {
            case VPACK:
                return new VpackSerde();
            case JSON:
                return new JsonSerde();
            default:
                throw new IllegalArgumentException(String.valueOf(contentType));
        }
    }

    public abstract VPackSlice createVPackSlice(byte[] buffer);

    public abstract byte[] serialize(Object value);

    public abstract <T> T deserialize(byte[] buffer, Type type);

    public final <T> T deserialize(final VPackSlice slice, final Type type) {
        return vPack.deserialize(slice, type);
    }

    public final <T> T deserialize(final byte[] buffer, final Class<T> clazz) {
        return deserialize(buffer, (Type) clazz);
    }

    public final <T> T deserialize(String fieldName, final byte[] buffer, final Class<T> clazz) {
        VPackSlice slice = createVPackSlice(buffer);
        return deserialize(slice.get(fieldName), (Type) clazz);
    }

    public final <T> T deserialize(final VPackSlice slice, final Class<T> clazz) {
        return deserialize(slice, (Type) clazz);
    }

    protected final VPackSlice serializeToVPackSlice(final Object value) {
        return vPack.serialize(value);
    }

}
