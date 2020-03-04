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
import com.arangodb.next.entity.velocypack.VPackDriverModule;
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

    public static ArangoSerde of(ContentType contentType) {
        switch (contentType) {
            case VPACK:
                return new VpackSerde();
            case JSON:
                return new JsonSerde();
            default:
                throw new IllegalArgumentException(String.valueOf(contentType));
        }
    }

    public abstract byte[] serialize(final Object value);

    public abstract <T> T deserialize(byte[] buffer, final Type type);

    public <T> T deserialize(byte[] buffer, Class<T> clazz) {
        return deserialize(buffer, (Type) clazz);
    }

    protected VPackSlice createVPackSlice(final Object value) {
        return vPack.serialize(value);
    }

    protected <T> T deserializeVPackSlice(final VPackSlice slice, final Type type) {
        return vPack.deserialize(slice, type);
    }

}
