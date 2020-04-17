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
import com.arangodb.next.api.reactive.ArangoClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 */
public interface CollectionApi extends ArangoClient {

    /**
     * @return all collections description
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-getting.html#reads-all-collections">API
     * Documentation</a>
     */
    default Flux<SimpleCollectionEntity> getCollections() {
        return getCollections(CollectionsReadParams.builder().excludeSystem(true).build());
    }

    /**
     * @param options request options
     * @return all collections description
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-getting.html#reads-all-collections">API
     * Documentation</a>
     */
    Flux<SimpleCollectionEntity> getCollections(CollectionsReadParams options);

    /**
     * Creates a collection for the given collection name and returns related information from the server.
     *
     * @param options request options
     * @return information about the collection
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#create-collection">API
     * Documentation</a>
     */
    default Mono<DetailedCollectionEntity> createCollection(CollectionCreateOptions options) {
        return createCollection(options, CollectionCreateParams.builder().build());
    }

    /**
     * Creates a collection for the given collection name and returns related information from the server.
     *
     * @param options request options
     * @param params  request params
     * @return information about the collection
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#create-collection">API
     * Documentation</a>
     */
    Mono<DetailedCollectionEntity> createCollection(CollectionCreateOptions options, CollectionCreateParams params);

    /**
     * Deletes the collection from the database.
     *
     * @return a Mono completing on operation completion
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#drops-collection">API
     * Documentation</a>
     */
    default Mono<Void> dropCollection(String name) {
        return dropCollection(name, CollectionDropParams.builder().build());
    }

    /**
     * Deletes the collection from the database.
     *
     * @param params request params
     * @return a Mono completing on operation completion
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#drops-collection">API
     * Documentation</a>
     */
    Mono<Void> dropCollection(String name, CollectionDropParams params);

    /**
     * @param name collection name
     * @return information about the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-information-about-a-collection">API
     * Documentation</a>
     */
    Mono<SimpleCollectionEntity> getCollection(String name);

    /**
     * @param name collection name
     * @return <code>true</code> if the collection exists, otherwise <code>false</code>
     */
    Mono<Boolean> existsCollection(String name);

    /**
     * @param name collection name
     * @return properties of the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#read-properties-of-a-collection">API
     * Documentation</a>
     */
    Mono<DetailedCollectionEntity> getCollectionProperties(String name);

    /**
     * Changes the properties of the collection
     *
     * @param name    collection name
     * @param options request options
     * @return information about the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-modifying.html#change-properties-of-a-collection">API
     * Documentation</a>
     */
    Mono<DetailedCollectionEntity> changeCollectionProperties(String name, CollectionChangePropertiesOptions options);

    /**
     * Renames the collection
     *
     * @param name    collection name
     * @param options request options
     * @return information about the collection
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-modifying.html#rename-collection">API
     * Documentation</a>
     */
    Mono<SimpleCollectionEntity> renameCollection(String name, CollectionRenameOptions options);

    /**
     * @param name collection name
     * @return the count of documents in the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-number-of-documents-in-a-collection">API
     * Documentation</a>
     */
    Mono<Long> getCollectionCount(String name);

    /**
     * @param name collection name
     * @return checksum for the specified collection
     * @apiNote this method is not available in a cluster
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-checksum-for-the-collection">API
     * Documentation</a>
     */
    default Mono<CollectionChecksumEntity> getCollectionChecksum(String name) {
        return getCollectionChecksum(name, CollectionChecksumParams.builder().build());
    }

    /**
     * @param name   collection name
     * @param params request params
     * @return checksum for the specified collection
     * @apiNote this method is not available in a cluster
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-checksum-for-the-collection">API
     * Documentation</a>
     */
    Mono<CollectionChecksumEntity> getCollectionChecksum(String name, CollectionChecksumParams params);

    /**
     * @param name collection name
     * @return statistics for the specified collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-statistics-for-a-collection">API
     * Documentation</a>
     */
    Mono<Object> getCollectionStatistics(String name);

    /**
     * Loads a collection into memory.
     *
     * @param name collection name
     * @return a Mono completing on operation completion
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-modifying.html#load-collection">API
     * Documentation</a>
     */
    Mono<Void> loadCollection(String name);

    /**
     * Loads a collection indexes into memory.
     *
     * @param name collection name
     * @return a Mono completing on operation completion
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-modifying.html#load-indexes-into-memory">API
     * Documentation</a>
     */
    Mono<Void> loadCollectionIndexes(String name);

    /**
     * Recalculates the document count of a collection
     *
     * @param name collection name
     * @return a Mono completing on operation completion
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-modifying.html#recalculate-count-of-a-collection">API
     * Documentation</a>
     */
    Mono<Void> recalculateCollectionCount(String name);

    /**
     * Removes all documents from the collection, but leaves the indexes intact
     *
     * @param name collection name
     * @return a Mono completing on operation completion
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#truncate-collection">API
     * Documentation</a>
     */
    Mono<Void> truncateCollection(String name);

    /**
     * @param name     collection name
     * @param document A projection of the document containing at least the shard key (_key or a custom attribute) for
     *                 which the responsible shard should be determined
     * @return Returns the ID of the shard that is responsible for the given document (if the document exists) or that
     * would be responsible if such document existed
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-getting.html#return-responsible-shard-for-a-document">API
     * Documentation</a>
     */
    Mono<String> getResponsibleShard(String name, Object document);

}
