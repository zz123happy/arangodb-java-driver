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

package com.arangodb.next.api.sync;

import com.arangodb.next.api.reactive.ArangoDB;

public interface ArangoDBSync {

    /**
     * @return the reactive version of this object
     */
    ArangoDB reactive();

    /**
     * Returns a {@link ArangoDatabaseSync} instance for the {@code _system} database.
     *
     * @return database handler
     */
    default ArangoDatabaseSync db() {
        return db("_system");
    }

    /**
     * Returns a {@link ArangoDatabaseSync} instance for the given database name.
     *
     * @param name Name of the database
     * @return database handler
     */
    ArangoDatabaseSync db(String name);

    /**
     * @return {@link ConversationManagerSync}
     */
    ConversationManagerSync getConversationManager();

    /**
     * Closes all connections and releases all the related resources.
     */
    default void shutdown() {
        reactive().shutdown().block();
    }

}
