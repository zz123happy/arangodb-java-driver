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

package com.arangodb.next.entity.model;

import com.arangodb.velocypack.annotations.VPackPOJOBuilder;
import org.immutables.value.Value;

/**
 * @author Michele Rastelli
 * @see <a
 * href="https://www.arangodb.com/docs/stable/http/miscellaneous-functions.html#return-server-database-engine-type">API
 * Documentation</a>
 */
@Value.Immutable
public interface Engine {

    @VPackPOJOBuilder
    static ImmutableEngine.Builder builder() {
        return ImmutableEngine.builder();
    }

    enum StorageEngineName {
        MMFILES("mmfiles"), ROCKSDB("rocksdb");

        private final String value;

        StorageEngineName(final String name) {
            value = name;
        }

        public static StorageEngineName of(final String value) {
            for (StorageEngineName e : values()) {
                if (e.value.equals(value)) {
                    return e;
                }
            }
            throw new IllegalArgumentException("Unknown value for StorageEngineName: " + value);
        }

    }

    StorageEngineName getName();

}
