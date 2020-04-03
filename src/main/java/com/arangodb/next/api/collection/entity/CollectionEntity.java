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
import com.arangodb.velocypack.annotations.VPackPOJOBuilder;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Mark Vollmary
 * @author Michele Rastelli
 * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html">API Documentation</a>
 */
@Value.Immutable
public interface CollectionEntity {

    @VPackPOJOBuilder
    static ImmutableCollectionEntity.Builder builder() {
        return ImmutableCollectionEntity.builder();
    }

    /**
     * @return unique identifier of the collection; deprecated
     */
    String getId();

    /**
     * @return literal name of this collection
     */
    String getName();

    /**
     * @return Attribute that is used in smart graphs. (cluster only)
     */
    @Nullable
    String getSmartGraphAttribute();

    /**
     * @return The maximal size setting for journals / datafiles in bytes. This option is only present for the MMFiles
     * storage engine.
     */
    @Nullable
    Long getJournalSize();

    /**
     * @return contains how many copies of each shard are kept on different DBServers.
     */
    @Nullable
    ReplicationFactor getReplicationFactor();

    /**
     * @return determines how many copies of each shard are required to be in sync on the different DBServers. If there
     * are less then these many copies in the cluster a shard will refuse to write. Writes to shards with enough
     * up-to-date copies will succeed at the same time however. The value of writeConcern can not be larger than
     * replicationFactor. (cluster only)
     */
    @Nullable
    Integer getMinReplicationFactor();

    /**
     * @return If true then creating, changing or removing documents will wait until the data has been synchronized to
     * disk.
     */
    @Nullable
    Boolean getWaitForSync();

    /**
     * @return Whether or not the collection will be compacted. This option is only present for the MMFiles storage engine.
     */
    @Nullable
    Boolean getDoCompact();

    /**
     * @return the number of index buckets
     * Only relevant for the MMFiles storage engine
     */
    @Nullable
    Integer getIndexBuckets();

    /**
     * @return the sharding strategy selected for the collection.
     * One of 'hash' or 'enterprise-hash-smart-edge'. (cluster only)
     */
    @Nullable
    ShardingStrategy getShardingStrategy();

    /**
     * @return If true then the collection data will be
     * kept in memory only and ArangoDB will not write or sync the data
     * to disk. This option is only present for the MMFiles storage engine.
     */
    @Nullable
    Boolean getIsVolatile();

    /**
     * @return The number of shards of the collection. (cluster only)
     */
    @Nullable
    Integer getNumberOfShards();

    /**
     * @return Only relevant for the MMFiles storage engine
     */
    CollectionStatus getStatus();

    /**
     * @return Unique identifier of the collection
     */
    String getGloballyUniqueId();

    /**
     * @return true if this is a system collection; usually name will start with an underscore.
     */
    boolean getIsSystem();

    /**
     * @return The type of the collection
     */
    CollectionType getType();

    /**
     * @return contains the names of document attributes that are used to determine the target shard for documents.
     * (cluster only)
     */
    @Nullable
    List<String> getShardKeys();

}
