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
    default Flux<CollectionEntity> getCollections() {
        return getCollections(CollectionsReadParams.builder().excludeSystem(true).build());
    }

    /**
     * @param options request options
     * @return all collections description
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-getting.html#reads-all-collections">API
     * Documentation</a>
     */
    Flux<CollectionEntity> getCollections(CollectionsReadParams options);

    /**
     * Creates a collection for the given collection name and returns related information from the server.
     *
     * @param options request options
     * @return information about the collection
     * @see <a href="https://www.arangodb.com/docs/stable/http/collection-creating.html#create-collection">API
     * Documentation</a>
     */
    default Mono<CollectionEntityDetailed> createCollection(CollectionCreateOptions options) {
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
    Mono<CollectionEntityDetailed> createCollection(CollectionCreateOptions options, CollectionCreateParams params);

    /**
     * Deletes the collection from the database.
     *
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
    Mono<CollectionEntity> getCollectionInfo(String name);

    /**
     * @param name collection name
     * @return properties of the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#read-properties-of-a-collection">API
     * Documentation</a>
     */
    Mono<CollectionEntityDetailed> getCollectionProperties(String name);

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
    Mono<CollectionEntityDetailed> changeCollectionProperties(String name, CollectionChangePropertiesOptions options);

    /**
     * @param name collection name
     * @return the count of documents in the collection
     * @see <a href=
     * "https://www.arangodb.com/docs/stable/http/collection-getting.html#return-number-of-documents-in-a-collection">API
     * Documentation</a>
     */
    Mono<Long> getCollectionCount(String name);

}
