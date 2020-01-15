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
import com.arangodb.velocypack.VPackSlice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author Michele Rastelli
 */
public final class VpackDeserializer implements ArangoDeserializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(VpackDeserializer.class);

    @Override
    public <T> T deserialize(final byte[] buffer, final Class<T> clazz) {
        if (clazz.equals(Version.class)) {
            return clazz.cast(deserializeVersion(buffer));
        } else if (clazz.equals(ClusterEndpoints.class)) {
            return clazz.cast(deserializeClusterEndpoints(buffer));
        } else if (clazz.equals(ErrorEntity.class)) {
            return clazz.cast(deserializeErrorEntity(buffer));
        } else {
            throw new IllegalArgumentException("Unsupported type: " + clazz.getName());
        }
    }

    private Version deserializeVersion(final byte[] buffer) {
        ImmutableVersion.Builder builder = ImmutableVersion.builder();
        VPackSlice slice = new VPackSlice(buffer);
        slice.objectIterator().forEachRemaining(field -> {
            switch (field.getKey()) {
                case "server":
                    builder.server(field.getValue().getAsString());
                    break;
                case "license":
                    builder.license(field.getValue().getAsString());
                    break;
                case "version":
                    builder.version(field.getValue().getAsString());
                    break;
                case "details":
                    field.getValue().objectIterator()
                            .forEachRemaining(detail -> builder.putDetails(detail.getKey(), detail.getValue().getAsString()));
                    break;
                default:
                    LOGGER.debug("Unknown field {}: skipping", field.getKey());
                    break;
            }
        });
        return builder.build();
    }

    private ClusterEndpoints deserializeClusterEndpoints(final byte[] buffer) {
        ImmutableClusterEndpoints.Builder builder = ImmutableClusterEndpoints.builder();
        VPackSlice slice = new VPackSlice(buffer);
        slice.objectIterator().forEachRemaining(field -> {
            switch (field.getKey()) {
                case "code":
                    builder.code(field.getValue().getAsInt());
                    break;
                case "error":
                    builder.error(field.getValue().getAsBoolean());
                    break;
                case "endpoints":
                    field.getValue().arrayIterator().forEachRemaining(endpointMap ->
                            endpointMap.objectIterator().forEachRemaining(endpoint ->
                                    builder.addEndpoints(Collections.singletonMap(endpoint.getKey(), endpoint.getValue().getAsString()))));
                    break;
                default:
                    LOGGER.debug("Unknown field {}: skipping", field.getKey());
                    break;
            }
        });
        return builder.build();
    }

    private ErrorEntity deserializeErrorEntity(final byte[] buffer) {
        ImmutableErrorEntity.Builder builder = ImmutableErrorEntity.builder();
        VPackSlice slice = new VPackSlice(buffer);
        slice.objectIterator().forEachRemaining(field -> {
            switch (field.getKey()) {
                case "code":
                    builder.code(field.getValue().getAsInt());
                    break;
                case "error":
                    builder.error(field.getValue().getAsBoolean());
                    break;
                case "errorMessage":
                    builder.errorMessage(field.getValue().getAsString());
                    break;
                case "errorNum":
                    builder.errorNum(field.getValue().getAsInt());
                    break;
                default:
                    LOGGER.debug("Unknown field {}: skipping", field.getKey());
                    break;
            }
        });
        return builder.build();
    }

}
