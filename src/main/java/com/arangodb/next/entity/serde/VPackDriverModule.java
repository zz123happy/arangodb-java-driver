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

import com.arangodb.next.entity.model.*;
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
        context.registerSerializer(ImmutableSatelliteReplicationFactor.class, VPackSerializers.SATELLITE_REPLICATION_FACTOR);
        context.registerSerializer(ImmutableNumericReplicationFactor.class, VPackSerializers.NUMERIC_REPLICATION_FACTOR);
        context.registerSerializer(Sharding.class, VPackSerializers.SHARDING);

        context.registerDeserializer(ReplicationFactor.class, VPackDeserializers.REPLICATION_FACTOR);
        context.registerDeserializer(Sharding.class, VPackDeserializers.SHARDING);
        context.registerDeserializer(Engine.StorageEngineName.class, VPackDeserializers.STORAGE_ENGINE_NAME);
    }

    @Override
    public <C extends VPackParserSetupContext<C>> void setup(final C context) {

    }

}
