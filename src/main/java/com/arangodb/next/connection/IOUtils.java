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

package com.arangodb.next.connection;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

/**
 * @author Michele Rastelli
 */
public class IOUtils {
    private static final int DEFAULT_INITIAL_CAPACITY = 256;
    private static final int DEFAULT_MAX_CAPACITY = Integer.MAX_VALUE;

    public static ByteBuf createBuffer() {
        return createBuffer(DEFAULT_INITIAL_CAPACITY);
    }

    public static ByteBuf createBuffer(int initialCapacity) {
        return createBuffer(initialCapacity, DEFAULT_MAX_CAPACITY);
    }

    public static ByteBuf createBuffer(int initialCapacity, int maxCapacity) {
        return PooledByteBufAllocator.DEFAULT.directBuffer(initialCapacity, maxCapacity);
    }

}
