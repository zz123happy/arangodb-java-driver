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


package com.arangodb.next.api.reactive;

import com.arangodb.next.api.collection.CollectionApi;
import com.arangodb.next.api.database.DatabaseApi;
import com.arangodb.next.entity.model.Engine;
import com.arangodb.next.entity.model.ServerRole;
import com.arangodb.next.entity.model.Version;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 * @author Mark Vollmary
 */
public interface ArangoDatabase extends ArangoClient {

    /**
     * @return main entry point for the ArangoDB driver
     */
    ArangoDB arango();

    /**
     * @return database name
     */
    String name();

    /**
     * @return DatabaseApi for the current database
     */
    DatabaseApi databaseApi();

    /**
     * @return CollectionApi for the current database
     */
    CollectionApi collectionApi();

    /**
     * @return server name and version number
     * @see <a href="https://www.arangodb.com/docs/stable/http/miscellaneous-functions.html#return-server-version">API
     * Documentation</a>
     */
    Mono<Version> getVersion();

    /**
     * Returns the server storage engine.
     *
     * @return the storage engine name
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/miscellaneous-functions.html#return-server-database-engine-type">API
     * Documentation</a>
     */
    Mono<Engine> getEngine();

    /**
     * @return the server role
     * @see <a href="https://www.arangodb.com/docs/stable/http/cluster-server-role.html">API Documentation</a>
     */
    Mono<ServerRole> getRole();

    /**
     * @param user The name of the user for which you want to query the databases
     * @return database names which are available for the specified user
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/user-management.html#list-the-accessible-databases-for-a-user">API
     * Documentation</a>
     */
    Flux<String> getAccessibleDatabasesFor(String user);
}
