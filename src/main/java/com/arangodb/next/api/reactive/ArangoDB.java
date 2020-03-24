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

import com.arangodb.next.api.sync.ArangoDBSync;
import com.arangodb.next.api.sync.impl.ArangoDBSyncImpl;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.model.Engine;
import com.arangodb.next.entity.model.ServerRole;
import com.arangodb.next.entity.model.Version;
import com.arangodb.next.entity.option.DBCreateOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 * @author Mark Vollmary
 */
public interface ArangoDB {

    /**
     * @return the synchronous blocking version of this object
     */
    default ArangoDBSync sync() {
        return new ArangoDBSyncImpl(this);
    }

    /**
     * @return {@link ConversationManager}
     */
    ConversationManager getConversationManager();

    /**
     * Closes all connections and releases all the related resources.
     *
     * @return a {@link Mono} completing when done
     */
    Mono<Void> shutdown();

    /**
     * Creates a new database with the given name.
     *
     * @param name Name of the database to create
     * @return a Mono completing when the db has been created successfully
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     */
    default Mono<Void> createDatabase(String name) {
        return createDatabase(DBCreateOptions.builder().name(name).build());
    }

    /**
     * Creates a new database with the given name.
     *
     * @param options Creation options
     * @return a Mono completing when the db has been created successfully
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     * @since ArangoDB 3.6.0
     */
    Mono<Void> createDatabase(DBCreateOptions options);

    /**
     * @param name db name
     * @return information about the database having the given name
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/database-database-management.html#information-of-the-database">API
     * Documentation</a>
     */
    Mono<DatabaseEntity> getDatabase(String name);

    /**
     * Retrieves a list of all existing databases
     *
     * @return all existing databases
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#list-of-databases">API
     * Documentation</a>
     */
    Flux<String> getDatabases();

    /**
     * Retrieves a list of all databases the current user can access
     *
     * @return all databases the current user can access
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/database-database-management.html#list-of-accessible-databases">API
     * Documentation</a>
     */
    Flux<String> getAccessibleDatabases();

    /**
     * List available database to the specified user
     *
     * @param user The name of the user for which you want to query the databases
     * @return database names which are available for the specified user
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/user-management.html#list-the-accessible-databases-for-a-user">API
     * Documentation</a>
     */
    Flux<String> getAccessibleDatabasesFor(String user);

    /**
     * Returns the server name and version number.
     *
     * @return the server version, number
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
     * Returns the server role.
     *
     * @return the server role
     * @see <a href="https://www.arangodb.com/docs/stable/http/cluster-server-role.html">API Documentation</a>
     */
    Mono<ServerRole> getRole();

}
