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
public enum ShardingStrategy {

    /**
     * default sharding used by ArangoDB Community Edition before version 3.4
     */
    COMMUNITY_COMPAT("community-compat"),

    /**
     * default sharding used by ArangoDB Enterprise Edition before version 3.4
     */
    ENTERPRISE_COMPAT("enterprise-compat"),

    /**
     * default sharding used by smart edge collections in ArangoDB Enterprise Edition before version 3.4
     */
    ENTERPRISE_SMART_EDGE_COMPAT("enterprise-smart-edge-compat"),

    /**
     * default sharding used for new collections starting from version 3.4 (excluding smart edge collections)
     */
    HASH("hash"),

    /**
     * default sharding used for new smart edge collections starting from version 3.4
     */
    ENTERPRISE_HASH_SMART_EDGE("enterprise-hash-smart-edge");

    private final String value;

    ShardingStrategy(final String shardingStrategyValue) {
        value = shardingStrategyValue;
    }

    public String getValue() {
        return value;
    }

    public static ShardingStrategy of(final String value) {
        for (ShardingStrategy shardingStrategy : ShardingStrategy.values()) {
            if (shardingStrategy.value.equals(value)) {
                return shardingStrategy;
            }
        }
        throw new IllegalArgumentException("Unknown ShardingStrategy value: " + value);
    }
}
