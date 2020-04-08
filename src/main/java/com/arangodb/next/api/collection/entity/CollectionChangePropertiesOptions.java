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

package com.arangodb.next.api.collection.entity;


import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * @author Mark Vollmary
 * @see <a href="https://www.arangodb.com/docs/stable/http/collection-modifying.html#change-properties-of-a-collection">API
 * Documentation</a>
 */
@Value.Immutable
public interface CollectionChangePropertiesOptions {

    static ImmutableCollectionChangePropertiesOptions.Builder builder() {
        return ImmutableCollectionChangePropertiesOptions.builder();
    }

    /**
     * @return whether the data is synchronized to disk before returning from a document create, update, replace or
     * removal operation.
     * Default: <code>false</code>
     */
    @Nullable
    Boolean getWaitForSync();

    /**
     * @return The maximal size of a journal or datafile in bytes. The value must be at least 1048576 (1 MiB).
     * Default: value from configuration parameter
     * @apiNote MMFiles storage engine only
     */
    @Nullable
    Long getJournalSize();

}
