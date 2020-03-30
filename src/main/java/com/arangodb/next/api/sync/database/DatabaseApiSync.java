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

package com.arangodb.next.api.sync.database;


import com.arangodb.next.api.reactive.database.DatabaseApi;
import com.arangodb.next.api.sync.ArangoClientSync;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.option.DBCreateOptions;

/**
 * @author Michele Rastelli
 */
public interface DatabaseApiSync extends ArangoClientSync<DatabaseApi> {

    /**
     * Creates a new database with the given name.
     *
     * @param name Name of the database to create
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     */
    default void createDatabase(String name) {
        reactive().createDatabase(name).block();
    }

    /**
     * Creates a new database with the given name.
     *
     * @param options Creation options
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     * @since ArangoDB 3.6.0
     */
    default void createDatabase(DBCreateOptions options) {
        reactive().createDatabase(options).block();
    }

    /**
     * @param name db name
     * @return information about the database having the given name
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#information-of-the-database">API
     * Documentation</a>
     */
    default DatabaseEntity getDatabase(String name) {
        return reactive().getDatabase(name).block();
    }


}
