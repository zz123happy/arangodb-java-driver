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

import com.arangodb.next.api.collection.entity.CollectionEntity;
import com.arangodb.next.api.collection.entity.CollectionType;
import com.arangodb.next.api.utils.CollectionApiProvider;
import com.arangodb.next.api.utils.TestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class CollectionApiTest {

    @ParameterizedTest(name = "{0}")
    @ArgumentsSource(CollectionApiProvider.class)
    void getCollections(TestContext ctx, CollectionApi collectionApi) {
        CollectionEntity graphs = collectionApi.getCollections()
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(graphs).isNotNull();
        assertThat(graphs.getIsSystem()).isTrue();
        assertThat(graphs.getType()).isEqualTo(CollectionType.DOCUMENT);

        CollectionEntity collection = collectionApi.getCollections(true)
                .filter(c -> c.getName().equals("_graphs"))
                .blockFirst();

        assertThat(collection).isNull();
    }

}