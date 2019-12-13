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

package com.arangodb.next.entity.codec;

import com.arangodb.next.entity.ClusterEndpoints;
import com.arangodb.next.entity.ImmutableClusterEndpoints;
import com.arangodb.next.entity.ImmutableVersion;
import com.arangodb.next.entity.Version;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Michele Rastelli
 */
public class JsonDeserializer implements ArangoDeserializer {

    @Override
    public <T> T deserialize(byte[] buffer, Class<T> clazz) {
        try {
            if (clazz.equals(Version.class)) {
                return clazz.cast(deserializeVersion(buffer));
            } else if (clazz.equals(ClusterEndpoints.class)) {
                return clazz.cast(deserializeClusterEndpoints(buffer));
            } else {
                throw new IllegalArgumentException("Unsupported type: " + clazz.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Version deserializeVersion(byte[] buffer) throws IOException {
        JsonParser parser = new JsonFactory().createParser(buffer);
        ImmutableVersion.Builder builder = ImmutableVersion.builder();

        parser.nextToken();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();
            if ("server".equals(name)) {
                parser.nextToken();
                builder.server(parser.getText());
            } else if ("license".equals(name)) {
                parser.nextToken();
                builder.license(parser.getText());
            } else if ("version".equals(name)) {
                parser.nextToken();
                builder.version(parser.getText());
            } else if ("details".equals(name)) {
                parser.nextToken();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String key = parser.getCurrentName();
                    parser.nextToken();
                    builder.putDetails(key, parser.getText());
                }
            }
        }

        parser.close();
        return builder.build();
    }

    private ClusterEndpoints deserializeClusterEndpoints(byte[] buffer) throws IOException {
        JsonParser parser = new JsonFactory().createParser(buffer);
        ImmutableClusterEndpoints.Builder builder = ImmutableClusterEndpoints.builder();

        parser.nextToken();

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();

            if ("code".equals(name)) {
                parser.nextToken();
                builder.code(parser.getIntValue());
            } else if ("error".equals(name)) {
                parser.nextToken();
                builder.error(parser.getBooleanValue());
            } else if ("endpoints".equals(name)) {
                parser.nextToken();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String key = parser.getCurrentName();
                        parser.nextToken();
                        builder.addEndpoints(Collections.singletonMap(key, parser.getText()));
                    }
                }
            }
        }

        parser.close();
        return builder.build();
    }

}
