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

package com.arangodb.next.api.sync.impl;


import com.arangodb.next.api.reactive.ArangoDatabase;
import com.arangodb.next.api.reactive.database.impl.DatabaseApiImpl;
import com.arangodb.next.api.sync.ArangoDatabaseSync;
import com.arangodb.next.api.sync.database.DatabaseApiSync;
import com.arangodb.next.api.sync.database.impl.DatabaseApiSyncImpl;

/**
 * @author Michele Rastelli
 */
public final class ArangoDatabaseSyncImpl implements ArangoDatabaseSync {

    private final ArangoDatabase delegate;

    public ArangoDatabaseSyncImpl(final ArangoDatabase arangoDatabase) {
        this.delegate = arangoDatabase;
    }

    @Override
    public ArangoDatabase reactive() {
        return delegate;
    }

    @Override
    public DatabaseApiSync databaseApi() {
        return new DatabaseApiSyncImpl(new DatabaseApiImpl(delegate));
    }

}
