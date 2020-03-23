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


package com.arangodb.next.api.sync;


import com.arangodb.next.communication.Conversation;

import java.util.Optional;

/**
 * @author Michele Rastelli
 */
public final class ThreadConversation implements AutoCloseable {
    private static final ThreadLocal<Conversation> THREAD_LOCAL_CONVERSATION = new ThreadLocal<>();

    public static Optional<Conversation> getThreadLocalConversation() {
        return Optional.ofNullable(THREAD_LOCAL_CONVERSATION.get());
    }

    public ThreadConversation(final Conversation conversation) {
        THREAD_LOCAL_CONVERSATION.set(conversation);
    }

    @Override
    public void close() {
        THREAD_LOCAL_CONVERSATION.remove();
    }
}
