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

package com.arangodb.next.entity.velocypack;

import com.arangodb.next.entity.model.ReplicationFactor;
import com.arangodb.velocypack.VPack;
import com.arangodb.velocypack.VPackSlice;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michele Rastelli
 */
class VPackDriverModuleTest {

    @Test
    void replicationFactor() {
        final VPack.Builder builder = new VPack.Builder();
        builder.registerModule(new VPackDriverModule());
        final VPack vpacker = builder.build();

        ReplicationFactor<?> originalSatellite = ReplicationFactor.ofSatellite();
        final VPackSlice serializedSatellite = vpacker.serialize(originalSatellite);
        Object deserializedSatellite = vpacker.deserialize(serializedSatellite, ReplicationFactor.class);
        assertThat(deserializedSatellite).isEqualTo(originalSatellite);

        ReplicationFactor<?> originalNumeric = ReplicationFactor.of(3);
        final VPackSlice serializedNumeric = vpacker.serialize(originalNumeric);
        Object deserializedNumeric = vpacker.deserialize(serializedNumeric, ReplicationFactor.class);
        assertThat(deserializedNumeric).isEqualTo(originalNumeric);
    }


}