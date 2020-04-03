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

package com.arangodb.next.api.database.entity;

import com.arangodb.next.api.entity.ReplicationFactor;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * @author Michele Rastelli
 */
@Value.Immutable
public interface DatabaseCreateOptions {

    static ImmutableDatabaseCreateOptions.Builder builder() {
        return ImmutableDatabaseCreateOptions.builder();
    }

    /**
     * @return a valid database name
     */
    String getName();

    /**
     * @return {@link Options}
     * @since ArangoDB 3.6.0
     */
    @Nullable
    Options getOptions();

    @Value.Immutable
    interface Options {

        static ImmutableOptions.Builder builder() {
            return ImmutableOptions.builder();
        }

        /**
         * @return Default replication factor for new collections created in this database. Special values include "satellite",
         * which will replicate the collection to every DB-server, and 1, which disables replication. (cluster only)
         */
        @Nullable
        ReplicationFactor getReplicationFactor();

        /**
         * @return Default write concern for new collections created in this database. It determines how many copies of each
         * shard are required to be in sync on the different DBServers. If there are less then these many copies in the
         * cluster a shard will refuse to write. Writes to shards with enough up-to-date copies will succeed at the same
         * time however. The value of writeConcern can not be larger than replicationFactor. (cluster only)
         */
        @Nullable
        Integer getWriteConcern();

        /**
         * @return The sharding method to use for new collections in this database.
         */
        @Nullable
        Sharding getSharding();

    }

}
