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

package com.arangodb.next.entity.serde;

import com.arangodb.next.api.collection.entity.CollectionStatus;
import com.arangodb.next.api.collection.entity.CollectionType;
import com.arangodb.next.api.collection.entity.KeyType;
import com.arangodb.next.api.collection.entity.ShardingStrategy;
import com.arangodb.next.api.database.entity.Sharding;
import com.arangodb.next.api.entity.ReplicationFactor;
import com.arangodb.next.entity.model.Engine;
import com.arangodb.velocypack.VPackModule;
import com.arangodb.velocypack.VPackParserModule;
import com.arangodb.velocypack.VPackParserSetupContext;
import com.arangodb.velocypack.VPackSetupContext;

/**
 * @author Mark Vollmary
 */
public final class VPackDriverModule implements VPackModule, VPackParserModule {

    @Override
    public <C extends VPackSetupContext<C>> void setup(final C context) {
        // --- --- --- --- ---
        // --- SERIALIZERS ---
        // --- --- --- --- ---

        context.registerSerializer(ReplicationFactor.class, VPackSerializers.REPLICATION_FACTOR);

        //region DatabaseApi
        context.registerSerializer(Sharding.class, VPackSerializers.SHARDING);
        //endregion

        //region CollectionApi
        context.registerSerializer(ShardingStrategy.class, VPackSerializers.SHARDING_STRATEGY);
        context.registerSerializer(KeyType.class, VPackSerializers.KEY_TYPE);
        context.registerSerializer(CollectionType.class, VPackSerializers.COLLECTION_TYPE);
        //endregion


        // --- --- --- --- --- -
        // --- DESERIALIZERS ---
        // --- --- --- --- --- -

        context.registerDeserializer(ReplicationFactor.class, VPackDeserializers.REPLICATION_FACTOR);
        context.registerDeserializer(Engine.StorageEngineName.class, VPackDeserializers.STORAGE_ENGINE_NAME);

        //region DatabaseApi
        context.registerDeserializer(Sharding.class, VPackDeserializers.SHARDING);
        //endregion

        //region CollectionApi
        context.registerDeserializer(CollectionType.class, VPackDeserializers.COLLECTION_TYPE);
        context.registerDeserializer(CollectionStatus.class, VPackDeserializers.COLLECTION_STATUS);
        context.registerDeserializer(ShardingStrategy.class, VPackDeserializers.SHARDING_STRATEGY);
        context.registerDeserializer(KeyType.class, VPackDeserializers.KEY_TYPE);
        //endregion
    }

    @Override
    public <C extends VPackParserSetupContext<C>> void setup(final C context) {

    }

}
