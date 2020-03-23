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

package com.arangodb.next.api;

import com.arangodb.next.communication.ArangoCommunication;
import com.arangodb.next.communication.Conversation;
import com.arangodb.next.entity.model.DatabaseEntity;
import com.arangodb.next.entity.option.DBCreateOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ArangoDB {

    Mono<Void> shutdown();

    //region CONVERSATION MANAGEMENT

    /**
     * Creates a new {@link Conversation} delegating {@link ArangoCommunication#createConversation(Conversation.Level)}
     *
     * @return a new conversation
     */
    Conversation createConversation(Conversation.Level level);

    /**
     * Creates a new conversation and binds {@param publisher} to it. All the requests performed by {@param publisher}
     * will be executed against the same coordinator. In case this is not possible it will behave according to the
     * specified conversation level {@link Conversation.Level}. Eg.:
     *
     * <pre>
     * {@code
     *         Mono<DatabaseEntity> db = arangoDB.requireConversation(
     *                 arangoDB
     *                         .createDatabase(name)
     *                         .then(arangoDB.getDatabase(name))
     *         );
     * }
     * </pre>
     *
     * @param publisher a {@link org.reactivestreams.Publisher} performing many db requests
     * @return a contextualized {@link org.reactivestreams.Publisher} with configured context conversation
     */
    <T> Mono<T> requireConversation(Mono<T> publisher);

    /**
     * Creates a new conversation and binds {@param publisher} to it. All the requests performed by {@param publisher}
     * will be executed against the same coordinator. In case this is not possible it will behave according to the
     * specified conversation level {@link Conversation.Level}. Eg.:
     *
     * <pre>
     * {@code
     *         Mono<DatabaseEntity> db = arangoDB.requireConversation(
     *                 arangoDB
     *                         .createDatabase(name)
     *                         .then(arangoDB.getDatabase(name))
     *         );
     * }
     * </pre>
     *
     * @param publisher a {@link org.reactivestreams.Publisher} performing many db requests
     * @return a contextualized {@link org.reactivestreams.Publisher} with configured context conversation
     */
    <T> Flux<T> requireConversation(Flux<T> publisher);

    /**
     * Creates a new conversation and binds {@param publisher} to it. All the requests performed by {@param publisher}
     * will be executed against the same coordinator. In case this is not possible it will behave according to the
     * specified conversation level {@link Conversation.Level}. Eg.:
     *
     * <pre>
     * {@code
     *         Mono<DatabaseEntity> db = arangoDB.preferConversation(
     *                 arangoDB
     *                         .createDatabase(name)
     *                         .then(arangoDB.getDatabase(name))
     *         );
     * }
     * </pre>
     *
     * @param publisher a {@link org.reactivestreams.Publisher} performing many db requests
     * @return a contextualized {@link org.reactivestreams.Publisher} with configured context conversation
     */
    <T> Mono<T> preferConversation(Mono<T> publisher);

    /**
     * Creates a new conversation and binds {@param publisher} to it. All the requests performed by {@param publisher}
     * will be executed against the same coordinator. In case this is not possible it will behave according to the
     * specified conversation level {@link Conversation.Level}. Eg.:
     *
     * <pre>
     * {@code
     *         Mono<DatabaseEntity> db = arangoDB.preferConversation(
     *                 arangoDB
     *                         .createDatabase(name)
     *                         .then(arangoDB.getDatabase(name))
     *         );
     * }
     * </pre>
     *
     * @param publisher a {@link org.reactivestreams.Publisher} performing many db requests
     * @return a contextualized {@link org.reactivestreams.Publisher} with configured context conversation
     */
    <T> Flux<T> preferConversation(Flux<T> publisher);
    //endregion


    /**
     * Creates a new database with the given name.
     *
     * @param name Name of the database to create
     * @return a Mono completing when the db has been created successfully
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     */
    default Mono<Void> createDatabase(String name) {
        return createDatabase(DBCreateOptions.builder().name(name).build());
    }

    /**
     * Creates a new database with the given name.
     *
     * @param options Creation options
     * @return a Mono completing when the db has been created successfully
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#create-database">API
     * Documentation</a>
     * @since ArangoDB 3.6.0
     */
    Mono<Void> createDatabase(DBCreateOptions options);

    /**
     * @param name db name
     * @return information about the database having the given name
     * @see <a href="https://www.arangodb.com/docs/stable/http/database-database-management.html#information-of-the-database">API
     * Documentation</a>
     */
    Mono<DatabaseEntity> getDatabase(String name);

}
