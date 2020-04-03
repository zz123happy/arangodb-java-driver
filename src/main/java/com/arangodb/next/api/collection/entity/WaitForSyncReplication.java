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

package com.arangodb.next.api.collection.entity;


/**
 * @author Michele Rastelli
 */
public enum WaitForSyncReplication {

    /**
     * faster server responses and don’t care about full replication
     */
    FALSE(0),

    /**
     * the server will only report success back to the client if all replicas have created the collection
     * (default)
     */
    TRUE(1);

    private final int value;

    WaitForSyncReplication(int waitForSyncReplicationValue) {
        value = waitForSyncReplicationValue;
    }

    public int getValue() {
        return value;
    }

    public static WaitForSyncReplication of(int value) {
        for (WaitForSyncReplication keyType : WaitForSyncReplication.values()) {
            if (keyType.value == value) {
                return keyType;
            }
        }
        throw new IllegalArgumentException("Unknown WaitForSyncReplication value: " + value);
    }

}
