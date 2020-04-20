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


import com.arangodb.next.api.entity.ReplicationFactor;
import com.arangodb.next.entity.ApiEntity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Michele Rastelli
 * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#create-collection">API
 * Documentation</a>
 */
@ApiEntity
public interface CollectionCreateOptions extends CollectionPropertiesOptions, CollectionNameOptions {

    static CollectionCreateOptionsBuilder builder() {
        return new CollectionCreateOptionsBuilder();
    }

    /**
     * @return this attribute determines how many copies of each shard are kept on different DBServers.
     * The value 1 means that only one copy (no synchronous replication) is kept. A value of k means that k-1 replicas
     * are kept. Any two copies reside on different DBServers. Replication between them is synchronous, that is, every
     * write operation to the “leader” copy will be replicated to all “follower” replicas, before the write operation is
     * reported successful.
     * Default: <code>1</code>
     * @apiNote cluster only
     */
    @Nullable
    ReplicationFactor getReplicationFactor();

    /**
     * @return Write concern for this collection. It determines how many copies of each shard are required
     * to be in sync on the different DBServers. If there are less then these many copies in the cluster a shard will
     * refuse to write. Writes to shards with enough up-to-date copies will succeed at the same time however. The value
     * of writeConcern can not be larger than replicationFactor.
     * Default: <code>1</code>
     * @apiNote cluster only
     */
    @Nullable
    Integer getMinReplicationFactor();

    /**
     * @return additional options for key generation
     */
    @Nullable
    KeyOptions getKeyOptions();

    /**
     * @return this attribute determines which document attributes are used to determine the target shard
     * for documents. Documents are sent to shards based on the values of their shard key attributes. The values of all
     * shard key attributes in a document are hashed, and the hash value is used to determine the target shard.
     * Default: <code>"_key"</code>
     * @apiNote Values of shard key attributes cannot be changed once set
     * @apiNote cluster only
     */
    @Nullable
    List<String> getShardKeys();

    /**
     * @return this value determines the number of shards to create for the collection.
     * Default: <code>1</code>
     * @apiNote cluster only
     */
    @Nullable
    Integer getNumberOfShards();

    /**
     * @return whether it is a system collection. System collection names should start with an underscore. End
     * users should normally create non-system collections only. API implementors may be required to create system
     * collections in very special occasions, but normally a regular collection will do.
     * Default: <code>false</code>
     */
    @Nullable
    Boolean getIsSystem();

    /**
     * @return the type of the collection to create.
     * Default: {@link CollectionType#DOCUMENT}
     */
    @Nullable
    CollectionType getType();

    /**
     * @return binds the specifics of sharding for the newly created
     * collection to follow that of a specified existing collection. Note: Using this parameter has consequences for the
     * prototype collection. It can no longer be dropped, before the sharding-imitating collections are dropped. Equally,
     * backups and restores of imitating collections alone will generate warnings (which can be overridden) about missing
     * sharding prototype.
     * @apiNote enterprise cluster only
     */
    @Nullable
    String getDistributeShardsLike();

    /**
     * @return specifies the name of the sharding strategy to use for the collection. The selected
     * shardingStrategy value will remain fixed for the collection and cannot be changed afterwards. This is important
     * to make the collection keep its sharding settings and always find documents already distributed to shards using
     * the same initial sharding algorithm.
     * Defaults:
     * - {@link ShardingStrategy#HASH}
     * - {@link ShardingStrategy#ENTERPRISE_HASH_SMART_EDGE} for all smart edge collections (enterprise)
     * @apiNote cluster only
     */
    @Nullable
    ShardingStrategy getShardingStrategy();

    /**
     * @return determines an attribute of the collection that
     * contains the shard key value of the referred-to smart join collection. Additionally, the shard key for a document
     * in this collection must contain the value of this attribute, followed by a colon, followed by the actual primary
     * key of the document.
     * @apiNote enterprise cluster only
     * @apiNote requires:
     * - the `distributeShardsLike` attribute of the collection to be set to the name of another collection
     * - shardKeys attribute of the collection to be set to a single shard key attribute, with an additional ‘:’ at the end
     * - value stored in the smartJoinAttribute the must be a string
     */
    @Nullable
    String getSmartJoinAttribute();

    /**
     * @return enables in-memory caching of documents and primary index entries
     */
    @Nullable
    Boolean getCacheEnabled();

}
