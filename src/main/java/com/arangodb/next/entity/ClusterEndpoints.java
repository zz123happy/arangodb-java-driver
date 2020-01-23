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

package com.arangodb.next.entity;


import com.arangodb.next.connection.HostDescription;
import org.immutables.value.Value;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Michele Rastelli
 */
@Value.Immutable
public interface ClusterEndpoints extends ArangoEntity {


    Set<Map<String, String>> getEndpoints();

    default Set<HostDescription> getHostDescriptions() {
        return getEndpoints().stream()
                .map(it -> it.get("endpoint"))
                .map(it -> it.replaceFirst(".*://", ""))
                .map(it -> {
                    if (it.matches("\\[.*]:.*")) {    // ipv6
                        return it
                                .replaceFirst("\\[", "")
                                .split("]:");
                    }
                    return it.split(":");
                })
                .map(it -> HostDescription.of(it[0], Integer.parseInt(it[1])))
                .collect(Collectors.toSet());
    }

}
