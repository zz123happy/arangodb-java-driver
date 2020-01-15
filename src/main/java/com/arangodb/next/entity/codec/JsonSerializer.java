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
import com.arangodb.next.entity.ErrorEntity;
import com.arangodb.next.entity.Version;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * @author Michele Rastelli
 */
public final class JsonSerializer implements ArangoSerializer {

    @Override
    public byte[] serialize(final Object value) {
        try {
            if (value instanceof Version) {
                return doSerialize((Version) value);
            } else if (value instanceof ClusterEndpoints) {
                return doSerialize((ClusterEndpoints) value);
            } else if (value instanceof ErrorEntity) {
                return doSerialize((ErrorEntity) value);
            } else {
                throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] doSerialize(final Version value) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(stream, JsonEncoding.UTF8);

        generator.writeStartObject();
        generator.writeStringField("license", value.getLicense());
        generator.writeStringField("server", value.getServer());
        generator.writeStringField("version", value.getVersion());

        Map<String, String> details = value.getDetails();
        generator.writeFieldName("details");
        if (details == null) {
            generator.writeNull();
        } else {
            generator.writeStartObject();
            for (Map.Entry<String, String> e : details.entrySet()) {
                generator.writeStringField(e.getKey(), e.getValue());
            }
            generator.writeEndObject();
        }

        generator.writeEndObject();
        generator.close();
        return stream.toByteArray();
    }

    private byte[] doSerialize(final ClusterEndpoints value) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(stream, JsonEncoding.UTF8);

        generator.writeStartObject();
        generator.writeBooleanField("error", value.getError());
        generator.writeNumberField("code", value.getCode());

        generator.writeArrayFieldStart("endpoints");
        for (Map<String, String> endpointMap : value.getEndpoints()) {
            generator.writeStartObject();
            for (Map.Entry<String, String> endpoint : endpointMap.entrySet()) {
                generator.writeStringField(endpoint.getKey(), endpoint.getValue());
            }
            generator.writeEndObject();
        }
        generator.writeEndArray();

        generator.writeEndObject();
        generator.close();
        return stream.toByteArray();
    }

    private byte[] doSerialize(final ErrorEntity value) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        JsonGenerator generator = new JsonFactory().createGenerator(stream, JsonEncoding.UTF8);

        generator.writeStartObject();
        generator.writeNumberField("code", value.getCode());
        generator.writeBooleanField("error", value.getError());
        generator.writeStringField("errorMessage", value.getErrorMessage());
        generator.writeNumberField("errorNum", value.getErrorNum());

        generator.writeEndObject();
        generator.close();
        return stream.toByteArray();
    }

}
