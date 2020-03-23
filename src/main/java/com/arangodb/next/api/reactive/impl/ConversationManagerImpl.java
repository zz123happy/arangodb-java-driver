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


package com.arangodb.next.api.reactive.impl;

import com.arangodb.next.api.reactive.ConversationManager;
import com.arangodb.next.communication.ArangoCommunication;
import com.arangodb.next.communication.Conversation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Michele Rastelli
 */
public class ConversationManagerImpl implements ConversationManager {

    private final ArangoCommunication communication;

    public ConversationManagerImpl(final ArangoCommunication communication) {
        this.communication = communication;
    }

    @Override
    public Conversation createConversation(Conversation.Level level) {
        return communication.createConversation(level);
    }

    @Override
    public <T> Mono<T> requireConversation(Mono<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.REQUIRED));
    }

    @Override
    public <T> Flux<T> requireConversation(Flux<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.REQUIRED));
    }

    @Override
    public <T> Mono<T> preferConversation(Mono<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.PREFERRED));
    }

    @Override
    public <T> Flux<T> preferConversation(Flux<T> publisher) {
        return wrapInConversation(publisher, createConversation(Conversation.Level.PREFERRED));
    }

    private <T> Mono<T> wrapInConversation(Mono<T> publisher, Conversation conversation) {
        return publisher.subscriberContext(sCtx -> sCtx.put(ArangoCommunication.CONVERSATION_CTX, conversation));
    }

    private <T> Flux<T> wrapInConversation(Flux<T> publisher, Conversation conversation) {
        return publisher.subscriberContext(sCtx -> sCtx.put(ArangoCommunication.CONVERSATION_CTX, conversation));
    }

}
