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

import com.arangodb.next.api.reactive.ArangoDB;
import com.arangodb.next.api.reactive.impl.ArangoDBImpl;
import com.arangodb.next.api.reactive.impl.ArangoDatabaseImpl;
import com.arangodb.next.api.sync.ArangoDBSync;
import com.arangodb.next.api.sync.ArangoDatabaseSync;
import com.arangodb.next.api.sync.ConversationManagerSync;

/**
 * @author Michele Rastelli
 */
public final class ArangoDBSyncImpl implements ArangoDBSync {

    private final ArangoDBImpl delegate;
    private final ConversationManagerSync conversationManager;

    public ArangoDBSyncImpl(final ArangoDBImpl arangoDB) {
        delegate = arangoDB;
        conversationManager = new ConversationManagerSyncImpl(arangoDB.getConversationManager());
    }

    @Override
    public ArangoDB reactive() {
        return delegate;
    }

    @Override
    public ArangoDatabaseSync db(final String name) {
        return new ArangoDatabaseSyncImpl(new ArangoDatabaseImpl(delegate, name));
    }

    @Override
    public ConversationManagerSync getConversationManager() {
        return conversationManager;
    }

}
