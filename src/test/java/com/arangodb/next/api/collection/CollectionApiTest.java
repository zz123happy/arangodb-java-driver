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
import com.arangodb.next.api.reactive.ConversationManager;
import com.arangodb.next.api.utils.CollectionApiProvider;
import com.arangodb.next.api.utils.TestContext;
import com.arangodb.next.communication.Conversation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.Collections;
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
        SimpleCollectionEntity graphs = collectionApi
                .getCollections(CollectionsReadParams.builder().excludeSystem(false).build())
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(graphs).isNotNull();
        assertThat(graphs.getName()).isNotNull();
        assertThat(graphs.getIsSystem()).isTrue();
        assertThat(graphs.getType()).isEqualTo(CollectionType.DOCUMENT);
        assertThat(graphs.getGloballyUniqueId()).isNotNull();

        SimpleCollectionEntity collection = collectionApi
                .getCollections(CollectionsReadParams.builder().excludeSystem(true).build())
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(collection).isNull();

        SimpleCollectionEntity graphsInfo = collectionApi.getCollection("_graphs").block();
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

        DetailedCollectionEntity createdCollection = collectionApi.createCollection(
                options,
                CollectionCreateParams.builder()
                        .enforceReplicationFactor(true)
                        .waitForSyncReplication(true)
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
                DetailedCollectionEntity shardLikeCollection = collectionApi.createCollection(shardLikeOptions).block();
                assertThat(shardLikeCollection).isNotNull();
                assertThat(shardLikeCollection.getDistributeShardsLike()).isEqualTo(createdCollection.getName());
            }
        }

        // readCollectionProperties
        DetailedCollectionEntity readCollectionProperties = collectionApi.getCollectionProperties(options.getName()).block();
        assertThat(readCollectionProperties).isEqualTo(createdCollection);

        // changeCollectionProperties
        DetailedCollectionEntity changedCollectionProperties = collectionApi.changeCollectionProperties(
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
                CollectionCreateParams.builder().waitForSyncReplication(true).build()
        ).block();

        assertThat(collectionApi.existsCollection(name).block()).isTrue();
        assertThat(collectionApi.getCollectionCount(name).block()).isEqualTo(0);

        ConversationManager cm = collectionApi.getConversationManager();
        Conversation conversation = cm.createConversation(Conversation.Level.REQUIRED);
        cm.useConversation(conversation, collectionApi.dropCollection(name)).block();

        assertThat(cm.useConversation(conversation, collectionApi.existsCollection(name)).block()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void createAndDropSystemCollection(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(
                CollectionCreateOptions.builder().name(name).isSystem(true).build(),
                CollectionCreateParams.builder().waitForSyncReplication(true).build()
        ).block();

        assertThat(collectionApi.existsCollection(name).block()).isTrue();

        ConversationManager cm = collectionApi.getConversationManager();
        Conversation conversation = cm.createConversation(Conversation.Level.REQUIRED);
        cm.useConversation(conversation, collectionApi.dropCollection(name, CollectionDropParams.builder().isSystem(true).build())).block();

        assertThat(cm.useConversation(conversation, collectionApi.existsCollection(name)).block()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void renameCollection(TestContext ctx, CollectionApi collectionApi) {
        assumeTrue(!ctx.isCluster());

        String name = "collection-" + UUID.randomUUID().toString();

        DetailedCollectionEntity created = collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        assertThat(created).isNotNull();
        assertThat(created.getName()).isEqualTo(name);

        String newName = "collection-" + UUID.randomUUID().toString();
        SimpleCollectionEntity renamed = collectionApi.renameCollection(name, CollectionRenameOptions.builder().name(newName).build()).block();
        assertThat(renamed).isNotNull();
        assertThat(renamed.getName()).isEqualTo(newName);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void truncateCollection(TestContext ctx, CollectionApi collectionApi) {

        // FIXME: add some docs to the collection

        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        collectionApi.truncateCollection(name).block();
        Long count = collectionApi.getCollectionCount(name).block();
        assertThat(count).isEqualTo(0L);
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void getCollectionChecksum(TestContext ctx, CollectionApi collectionApi) {
        assumeTrue(!ctx.isCluster());

        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        CollectionChecksumEntity collectionChecksumEntity = collectionApi.getCollectionChecksum(name).block();
        assertThat(collectionChecksumEntity).isNotNull();
        assertThat(collectionChecksumEntity.getChecksum()).isNotNull();
        assertThat(collectionChecksumEntity.getRevision()).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void getCollectionStatistics(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        Object collectionStatistics = collectionApi.getCollectionStatistics(name).block();
        System.out.println(collectionStatistics);
        assertThat(collectionStatistics).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void loadCollection(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        collectionApi.loadCollection(name).block();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void loadCollectionIndexes(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        collectionApi.loadCollectionIndexes(name).block();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void recalculateCollectionCount(TestContext ctx, CollectionApi collectionApi) {
        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        collectionApi.recalculateCollectionCount(name).block();
    }

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void getResponsibleShard(TestContext ctx, CollectionApi collectionApi) {
        assumeTrue(ctx.isCluster());

        String name = "collection-" + UUID.randomUUID().toString();
        collectionApi.createCollection(CollectionCreateOptions.builder().name(name).build()).block();
        String responsibleShard = collectionApi.getResponsibleShard(name, Collections.singletonMap("_key", "aaa")).block();
        assertThat(responsibleShard).isNotNull();
    }

}
