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

package com.arangodb.next.api.collection;

import com.arangodb.next.api.collection.entity.*;
import com.arangodb.next.api.entity.ReplicationFactor;
import com.arangodb.next.api.utils.CollectionApiProvider;
import com.arangodb.next.api.utils.TestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * @author Michele Rastelli
 */
class CollectionApiTest {

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void getCollectionsAndGetCollectionInfo(TestContext ctx, CollectionApi collectionApi) {
        CollectionEntity graphs = collectionApi
                .getCollections(CollectionsReadParams.builder().excludeSystem(false).build())
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(graphs).isNotNull();
        assertThat(graphs.getName()).isNotNull();
        assertThat(graphs.getIsSystem()).isTrue();
        assertThat(graphs.getStatus()).isNotNull();
        assertThat(graphs.getType()).isEqualTo(CollectionType.DOCUMENT);
        assertThat(graphs.getGloballyUniqueId()).isNotNull();

        CollectionEntity collection = collectionApi
                .getCollections(CollectionsReadParams.builder().excludeSystem(true).build())
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(collection).isNull();

        CollectionEntity graphsInfo = collectionApi.getCollectionInfo("_graphs").block();
        assertThat(graphsInfo).isEqualTo(graphs);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void createCollectionAndGetCollectionProperties(TestContext ctx, CollectionApi collectionApi) {
        CollectionCreateOptions options = CollectionCreateOptions.builder()
                .name("myCollection-" + UUID.randomUUID().toString())
                .replicationFactor(ReplicationFactor.of(2))
                .minReplicationFactor(1)
                .keyOptions(KeyOptions.builder()
                        .allowUserKeys(false)
                        .type(KeyType.UUID)
                        .build()
                )
                .waitForSync(true)
                .addShardKeys("a:")
                .numberOfShards(3)
                .isSystem(false)
                .type(CollectionType.DOCUMENT)
                .shardingStrategy(ShardingStrategy.HASH)
                .smartJoinAttribute("d")
                .cacheEnabled(true)
                .build();

        CollectionEntityDetailed createdCollection = collectionApi.createCollection(
                options,
                CollectionCreateParams.builder()
                        .enforceReplicationFactor(EnforceReplicationFactor.TRUE)
                        .waitForSyncReplication(WaitForSyncReplication.TRUE)
                        .build()
        ).block();

        assertThat(createdCollection).isNotNull();
        assertThat(createdCollection.getName()).isEqualTo(options.getName());
        assertThat(createdCollection.getKeyOptions()).isEqualTo(options.getKeyOptions());
        assertThat(createdCollection.getWaitForSync()).isEqualTo(options.getWaitForSync());
        assertThat(createdCollection.getIsSystem()).isEqualTo(options.getIsSystem());
        assertThat(createdCollection.getType()).isEqualTo(options.getType());
        assertThat(createdCollection.getGloballyUniqueId()).isNotNull();
        assertThat(createdCollection.getCacheEnabled()).isEqualTo(options.getCacheEnabled());

        if (ctx.isCluster()) {
            assertThat(createdCollection.getReplicationFactor()).isEqualTo(options.getReplicationFactor());
            assertThat(createdCollection.getMinReplicationFactor()).isEqualTo(options.getMinReplicationFactor());
            assertThat(createdCollection.getShardKeys()).isEqualTo(options.getShardKeys());
            assertThat(createdCollection.getNumberOfShards()).isEqualTo(options.getNumberOfShards());
            assertThat(createdCollection.getShardingStrategy()).isEqualTo(options.getShardingStrategy());

            if (ctx.isEnterprise()) {
                assertThat(createdCollection.getSmartJoinAttribute()).isNotNull();
                CollectionCreateOptions shardLikeOptions = CollectionCreateOptions.builder()
                        .name("shardLikeCollection-" + UUID.randomUUID().toString())
                        .distributeShardsLike(options.getName())
                        .shardKeys(options.getShardKeys())
                        .build();
                CollectionEntityDetailed shardLikeCollection = collectionApi.createCollection(shardLikeOptions).block();
                assertThat(shardLikeCollection).isNotNull();
                assertThat(shardLikeCollection.getDistributeShardsLike()).isEqualTo(createdCollection.getName());
            }
        }

        // readCollectionProperties
        CollectionEntityDetailed readCollectionProperties = collectionApi.getCollectionProperties(options.getName()).block();
        assertThat(readCollectionProperties).isEqualTo(createdCollection);

        // changeCollectionProperties
        CollectionEntityDetailed changedCollectionProperties = collectionApi.changeCollectionProperties(
                options.getName(),
                CollectionChangePropertiesOptions.builder().waitForSync(!createdCollection.getWaitForSync()).build()
        ).block();
        assertThat(changedCollectionProperties).isNotNull();
        assertThat(changedCollectionProperties.getWaitForSync()).isEqualTo(!createdCollection.getWaitForSync());
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void countAndDropCollection(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(
                CollectionCreateOptions.builder().name(name).build(),
                CollectionCreateParams.builder().waitForSyncReplication(WaitForSyncReplication.TRUE).build()
        ).block();

        // FIXME: replace with exists()
        assertThat(collectionApi.getCollections().collectList().block().stream().anyMatch(it -> name.equals(it.getName()))).isTrue();
        assertThat(collectionApi.getCollectionCount(name).block()).isEqualTo(0);

        collectionApi.dropCollection(name).block();

        // FIXME: replace with !exists()
        assertThat(collectionApi.getCollections().collectList().block().stream().anyMatch(it -> name.equals(it.getName()))).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void createAndDropSystemCollection(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(
                CollectionCreateOptions.builder().name(name).isSystem(true).build(),
                CollectionCreateParams.builder().waitForSyncReplication(WaitForSyncReplication.TRUE).build()
        ).block();

        // FIXME: replace with exists()
        assertThat(collectionApi.getCollections().collectList().block().stream().anyMatch(it -> name.equals(it.getName()))).isTrue();

        collectionApi.dropCollection(name, CollectionDropParams.builder().isSystem(true).build()).block();

        // FIXME: replace with !exists()
        assertThat(collectionApi.getCollections().collectList().block().stream().anyMatch(it -> name.equals(it.getName()))).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void renameCollection(TestContext ctx, CollectionApi collectionApi) {
        assumeTrue(!ctx.isCluster());

        String name = "collection-" + UUID.randomUUID().toString();

        CollectionEntityDetailed created = collectionApi.createCollection(CollectionCreateOptions.builder().name(name).isSystem(true).build()).block();
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(name);

        String newName = "collection-" + UUID.randomUUID().toString();
        CollectionEntity renamed = collectionApi.rename(name, CollectionRenameOptions.builder().name(newName).build()).block();
        assertThat(renamed).isNotNull();
        assertThat(renamed.getName()).isEqualTo(newName);
    }


}