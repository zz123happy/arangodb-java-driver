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

import com.arangodb.velocypack.VPackParser;
import com.arangodb.velocypack.VPackSlice;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;


/**
 * @author Michele Rastelli
 */
public final class JsonSerde extends ArangoSerde {

    private final VPackParser parser = new VPackParser.Builder().build();

    @Override
    public VPackSlice createVPackSlice(final byte[] buffer) {
        return parser.fromJson(new String(buffer));
    }

    @Override
    public byte[] serialize(final Object value) {
        return parser.toJson(serializeToVPackSlice(value), true).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <T> T deserialize(final byte[] buffer, final Type type) {
        return deserialize(parser.fromJson(new String(buffer)), type);
    }

}
