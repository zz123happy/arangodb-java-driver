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

package com.arangodb.async;

import com.arangodb.entity.ArangoDBEngine;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.entity.StreamTransactionEntity;
import com.arangodb.model.AqlQueryOptions;
import com.arangodb.model.DocumentCreateOptions;
import com.arangodb.model.DocumentReadOptions;
import com.arangodb.model.StreamTransactionOptions;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assume.assumeTrue;

/**
 * @author Michele Rastelli
 */
public class ConcurrentStreamTransactionsTest extends BaseTest {

    private static final String COLLECTION_NAME = "db_concurrent_stream_transactions_test";

    public ConcurrentStreamTransactionsTest() throws ExecutionException, InterruptedException {
        if (db.collection(COLLECTION_NAME).exists().get())
            db.collection(COLLECTION_NAME).drop().get();

        db.createCollection(COLLECTION_NAME, null).get();
    }

    @After
    public void teardown() throws ExecutionException, InterruptedException {
        if (db.collection(COLLECTION_NAME).exists().get())
            db.collection(COLLECTION_NAME).drop().get();
    }

    @Test
    public void concurrentWriteWithinSameTransaction() throws ExecutionException, InterruptedException {
        assumeTrue(isAtLeastVersion(3, 5));
        assumeTrue(isStorageEngine(ArangoDBEngine.StorageEngineName.rocksdb));

        StreamTransactionEntity tx = db.beginStreamTransaction(
                new StreamTransactionOptions().writeCollections(COLLECTION_NAME)).join();

        List<CompletableFuture<DocumentCreateEntity<BaseDocument>>> reqs = IntStream.range(0, 100)
                .mapToObj(it -> db.collection(COLLECTION_NAME)
                        .insertDocument(new BaseDocument(), new DocumentCreateOptions().streamTransactionId(tx.getId())))
                .collect(Collectors.toList());

        List<DocumentCreateEntity<BaseDocument>> results = reqs.stream().map(CompletableFuture::join).collect(Collectors.toList());
        db.commitStreamTransaction(tx.getId()).join();

        results.forEach(it -> {
            assertThat(it.getKey(), is(notNullValue()));
            assertThat(db.collection(COLLECTION_NAME).documentExists(it.getKey()).join(), is(true));
        });
    }

    @Test
    public void concurrentAqlWriteWithinSameTransaction() throws ExecutionException, InterruptedException {
        assumeTrue(isAtLeastVersion(3, 5));
        assumeTrue(isStorageEngine(ArangoDBEngine.StorageEngineName.rocksdb));

        StreamTransactionEntity tx = db.beginStreamTransaction(
                new StreamTransactionOptions().writeCollections(COLLECTION_NAME)).join();

        List<CompletableFuture<ArangoCursorAsync<BaseDocument>>> reqs = IntStream.range(0, 100)
                .mapToObj(it -> {
                    Map<String, Object> params = new HashMap<>();
                    params.put("doc", new BaseDocument("key-" + UUID.randomUUID().toString()));
                    params.put("@col", COLLECTION_NAME);
                    return db.query("INSERT @doc INTO @@col RETURN NEW", params,
                            new AqlQueryOptions().streamTransactionId(tx.getId()), BaseDocument.class);
                })
                .collect(Collectors.toList());

        List<ArangoCursorAsync<BaseDocument>> results = reqs.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        db.commitStreamTransaction(tx.getId()).join();

        results.forEach(it -> {
            String key = it.iterator().next().getKey();
            assertThat(key, is(notNullValue()));
            assertThat(db.collection(COLLECTION_NAME).documentExists(key).join(), is(true));
        });
    }


    @Test
    public void failingAqlFromBTS57() throws ExecutionException, InterruptedException {
        assumeTrue(isAtLeastVersion(3, 5));
        assumeTrue(isStorageEngine(ArangoDBEngine.StorageEngineName.rocksdb));

        String key = "key-" + UUID.randomUUID().toString();
        String id = COLLECTION_NAME + "/" + key;
        db.collection(COLLECTION_NAME).insertDocument(new BaseDocument(key)).join();

        StreamTransactionEntity tx = db.beginStreamTransaction(
                new StreamTransactionOptions().writeCollections(COLLECTION_NAME)).join();

        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("@col", COLLECTION_NAME);
        CompletableFuture<ArangoCursorAsync<Boolean>> req = db.query("" +
                        "LET d = DOCUMENT(@id)\n" +
                        "UPDATE d WITH { \"aaa\": \"aaa\" } IN @@col " +
                        "RETURN true",
                params,
                new AqlQueryOptions().streamTransactionId(tx.getId()), Boolean.class);

        req.join();
        db.commitStreamTransaction(tx.getId()).join();
    }

    @Test
    public void concurrentReadWithinSameTransaction() throws ExecutionException, InterruptedException {
        assumeTrue(isAtLeastVersion(3, 5));
        assumeTrue(isStorageEngine(ArangoDBEngine.StorageEngineName.rocksdb));

        String key = "key-" + UUID.randomUUID().toString();
        db.collection(COLLECTION_NAME).insertDocument(new BaseDocument(key)).join();

        StreamTransactionEntity tx = db.beginStreamTransaction(
                new StreamTransactionOptions().readCollections(COLLECTION_NAME)).join();

        List<CompletableFuture<BaseDocument>> reqs = IntStream.range(0, 100)
                .mapToObj(it -> db.collection(COLLECTION_NAME)
                        .getDocument(key, BaseDocument.class, new DocumentReadOptions().streamTransactionId(tx.getId())))
                .collect(Collectors.toList());

        List<BaseDocument> results = reqs.stream().map(CompletableFuture::join).collect(Collectors.toList());
        db.commitStreamTransaction(tx.getId()).join();

        results.forEach(it -> assertThat(it.getKey(), is(notNullValue())));
    }

    @Test
    public void concurrentAqlReadWithinSameTransaction() throws ExecutionException, InterruptedException {
        assumeTrue(isAtLeastVersion(3, 5));
        assumeTrue(isStorageEngine(ArangoDBEngine.StorageEngineName.rocksdb));

        String key = "key-" + UUID.randomUUID().toString();
        String id = COLLECTION_NAME + "/" + key;
        db.collection(COLLECTION_NAME).insertDocument(new BaseDocument(key)).join();

        StreamTransactionEntity tx = db.beginStreamTransaction(
                new StreamTransactionOptions().readCollections(COLLECTION_NAME)).join();

        List<CompletableFuture<ArangoCursorAsync<BaseDocument>>> reqs = IntStream.range(0, 100)
                .mapToObj(it -> db.query(
                        "RETURN DOCUMENT(@id)",
                        Collections.singletonMap("id", id),
                        new AqlQueryOptions().streamTransactionId(tx.getId()), BaseDocument.class))
                .collect(Collectors.toList());

        List<ArangoCursorAsync<BaseDocument>> results = reqs.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        db.commitStreamTransaction(tx.getId()).join();
        results.forEach(it -> assertThat(it.iterator().next().getKey(), is(key)));
    }

}
