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
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Michele Rastelli
 * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#create-collection">API
 * Documentation</a>
 */
@Value.Immutable
public interface CollectionCreateOptions {

    static ImmutableCollectionCreateOptions.Builder builder() {
        return ImmutableCollectionCreateOptions.builder();
    }

    /**
     * @return The name of the collection
     */
    String getName();

    /**
     * @return The maximal size of a journal or datafile in bytes. The value must be at least 1048576 (1 MiB).
     * (The default is a configuration parameter) This option is meaningful for the MMFiles storage engine only.
     */
    @Nullable
    Long getJournalSize();

    /**
     * @return in a cluster, this attribute determines how many copies of each shard are kept on different DBServers.
     * The value 1 means that only one copy (no synchronous replication) is kept. A value of k means that k-1 replicas
     * are kept. Any two copies reside on different DBServers. Replication between them is synchronous, that is, every
     * write operation to the “leader” copy will be replicated to all “follower” replicas, before the write operation is
     * reported successful. (The default is 1)
     */
    @Nullable
    ReplicationFactor getReplicationFactor();

    /**
     * @return Write concern for this collection (default: 1). It determines how many copies of each shard are required
     * to be in sync on the different DBServers. If there are less then these many copies in the cluster a shard will
     * refuse to write. Writes to shards with enough up-to-date copies will succeed at the same time however. The value
     * of writeConcern can not be larger than replicationFactor. (cluster only)
     */
    @Nullable
    Integer getMinReplicationFactor();

    /**
     * @return additional options for key generation
     */
    @Nullable
    KeyOptions getKeyOptions();

    /**
     * @return If true then the data is synchronized to disk before returning from a document create, update, replace or
     * removal operation. (default: <code>false</code>)
     */
    @Nullable
    Boolean getWaitForSync();

    /**
     * @return whether or not the collection will be compacted (default is <code>true</code>) This option is meaningful
     * for the MMFiles storage engine only.
     */
    @Nullable
    Boolean getDoCompact();

    /**
     * @return If true then the collection data is kept in-memory only and not made persistent. Unloading the collection
     * will cause the collection data to be discarded. Stopping or re-starting the server will also cause full loss of
     * data in the collection. Setting this option will make the resulting collection be slightly faster than regular
     * collections because ArangoDB does not enforce any synchronization to disk and does not calculate any CRC
     * hecksums for datafiles (as there are no datafiles). This option should therefore be used for cache-type
     * collections only, and not for data that cannot be re-created otherwise. (The default is <code>false</code>) This
     * option is meaningful for the MMFiles storage engine only.
     */
    @Nullable
    Boolean getIsVolatile();

    /**
     * @return in a cluster, this attribute determines which document attributes are used to determine the target shard
     * for documents. Documents are sent to shards based on the values of their shard key attributes. The values of all
     * shard key attributes in a document are hashed, and the hash value is used to determine the target shard.
     * (The default is [ “_key” ])
     *
     * @apiNote Values of shard key attributes cannot be changed once set. This option is meaningless in a single server
     * setup.
     */
    @Nullable
    List<String> getShardKeys();

    /**
     * @return in a cluster, this value determines the number of shards to create for the collection. In a single server
     * setup, this option is meaningless. (The default is 1)
     */
    @Nullable
    Integer getNumberOfShards();

    /**
     * @return If true, create a system collection. In this case collection-name should start with an underscore. End
     * users should normally create non-system collections only. API implementors may be required to create system
     * collections in very special occasions, but normally a regular collection will do. (The default is <code>false</code>)
     */
    @Nullable
    Boolean getIsSystem();

    /**
     * @return the type of the collection to create (The default is {@link CollectionType#DOCUMENT})
     */
    @Nullable
    CollectionType getType();

    /**
     * @return The number of buckets into which indexes using a hash table are split. The default is 16 and this number
     * has to be a power of 2 and less than or equal to 1024.
     */
    @Nullable
    Integer getIndexBuckets();

    /**
     * @return in an Enterprise Edition cluster, this attribute binds the specifics of sharding for the newly created
     * collection to follow that of a specified existing collection. Note: Using this parameter has consequences for the
     * prototype collection. It can no longer be dropped, before the sharding-imitating collections are dropped. Equally,
     * backups and restores of imitating collections alone will generate warnings (which can be overridden) about missing
     * sharding prototype.
     */
    @Nullable
    String getDistributeShardsLike();

    /**
     * @return This attribute specifies the name of the sharding strategy to use for the collection. Since ArangoDB 3.4
     * there are different sharding strategies to select from when creating a new collection. The selected
     * shardingStrategy value will remain fixed for the collection and cannot be changed afterwards. This is important
     * to make the collection keep its sharding settings and always find documents already distributed to shards using
     * the same initial sharding algorithm.
     * Defaults:
     * - {@link ShardingStrategy#HASH} for for all collections
     * - {@link ShardingStrategy#ENTERPRISE_HASH_SMART_EDGE} for all smart edge collections (requires the Enterprise Edition of ArangoDB)
     */
    @Nullable
    ShardingStrategy getShardingStrategy();

    /**
     * @return In an Enterprise Edition cluster, this attribute determines an attribute of the collection that must
     * contain the shard key value of the referred-to smart join collection. Additionally, the shard key for a document
     * in this collection must contain the value of this attribute, followed by a colon, followed by the actual primary
     * key of the document.
     *
     * @apiNote This feature can only be used in the Enterprise Edition and requires the distributeShardsLike attribute
     * of the collection to be set to the name of another collection. It also requires the shardKeys attribute of the
     * collection to be set to a single shard key attribute, with an additional ‘:’ at the end. A further restriction is
     * that whenever documents are stored or updated in the collection, the value stored in the smartJoinAttribute must
     * be a string.
     */
    @Nullable
    String getSmartJoinAttribute();

}
