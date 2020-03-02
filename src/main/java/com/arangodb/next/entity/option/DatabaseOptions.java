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

package com.arangodb.next.entity.option;

import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.next.entity.model.Sharding;
import org.immutables.value.Value;

import javax.annotation.Nullable;

/**
 * @author Michele Rastelli
 * @since ArangoDB 3.6.0
 */
@Value.Immutable
public interface DatabaseOptions {

    static ImmutableDatabaseOptions.Builder builder() {
        return ImmutableDatabaseOptions.builder();
    }

    /**
     * @return Default replication factor for new collections created in this database. Special values include "satellite",
     * which will replicate the collection to every DB-server, and 1, which disables replication. (cluster only)
     */
    @Nullable
    ReplicationFactor<?> getReplicationFactor();

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
