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

import com.arangodb.next.entity.*;
import com.arangodb.next.exceptions.SerdeException;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

/**
 * @author Michele Rastelli
 */
public final class JsonDeserializer implements ArangoDeserializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(JsonDeserializer.class);
    private static final String SKIP_FIELD_MSG = "Unknown field {}: skipping";

    @Override
    public <T> T deserialize(final byte[] buffer, final Class<T> clazz) {
        try {
            if (clazz.equals(Version.class)) {
                return clazz.cast(deserializeVersion(buffer));
            } else if (clazz.equals(ClusterEndpoints.class)) {
                return clazz.cast(deserializeClusterEndpoints(buffer));
            } else if (clazz.equals(ErrorEntity.class)) {
                return clazz.cast(deserializeErrorEntity(buffer));
            } else {
                throw new IllegalArgumentException("Unsupported type: " + clazz.getName());
            }
        } catch (IOException e) {
            throw SerdeException.of(e);
        }
    }

    private Version deserializeVersion(final byte[] buffer) throws IOException {
        try (JsonParser parser = new JsonFactory().createParser(buffer)) {
            ImmutableVersion.Builder builder = ImmutableVersion.builder();

            parser.nextToken();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getCurrentName();
                switch (name) {
                    case "server":
                        parser.nextToken();
                        builder.server(parser.getText());
                        break;
                    case "license":
                        parser.nextToken();
                        builder.license(parser.getText());
                        break;
                    case "version":
                        parser.nextToken();
                        builder.version(parser.getText());
                        break;
                    case "details":
                        parser.nextToken();
                        while (parser.nextToken() != JsonToken.END_OBJECT) {
                            String key = parser.getCurrentName();
                            parser.nextToken();
                            builder.putDetails(key, parser.getText());
                        }
                        break;
                    default:
                        LOGGER.debug(SKIP_FIELD_MSG, name);
                        break;
                }
            }
            return builder.build();
        }
    }

    private ClusterEndpoints deserializeClusterEndpoints(final byte[] buffer) throws IOException {
        try (JsonParser parser = new JsonFactory().createParser(buffer)) {
            ImmutableClusterEndpoints.Builder builder = ImmutableClusterEndpoints.builder();

            parser.nextToken();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getCurrentName();

                switch (name) {
                    case "code":
                        parser.nextToken();
                        builder.code(parser.getIntValue());
                        break;
                    case "error":
                        parser.nextToken();
                        builder.error(parser.getBooleanValue());
                        break;
                    case "endpoints":
                        parser.nextToken();
                        while (parser.nextToken() != JsonToken.END_ARRAY) {
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                String key = parser.getCurrentName();
                                parser.nextToken();
                                builder.addEndpoints(Collections.singletonMap(key, parser.getText()));
                            }
                        }
                        break;
                    default:
                        LOGGER.debug(SKIP_FIELD_MSG, name);
                        break;
                }
            }
            return builder.build();
        }
    }

    private ErrorEntity deserializeErrorEntity(final byte[] buffer) throws IOException {
        try (JsonParser parser = new JsonFactory().createParser(buffer)) {
            ImmutableErrorEntity.Builder builder = ImmutableErrorEntity.builder();

            parser.nextToken();

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String name = parser.getCurrentName();

                switch (name) {
                    case "code":
                        parser.nextToken();
                        builder.code(parser.getIntValue());
                        break;
                    case "error":
                        parser.nextToken();
                        builder.error(parser.getBooleanValue());
                        break;
                    case "errorMessage":
                        parser.nextToken();
                        builder.errorMessage(parser.getText());
                        break;
                    case "errorNum":
                        parser.nextToken();
                        builder.errorNum(parser.getIntValue());
                        break;
                    default:
                        LOGGER.debug(SKIP_FIELD_MSG, name);
                        break;
                }
            }
            return builder.build();
        }
    }

}
