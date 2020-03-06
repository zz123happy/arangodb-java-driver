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


package deployments;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;

/**
 * @author Michele Rastelli
 */
public enum ReusableNetwork implements Network {
    INSTANCE;

    private final String NAME = "arangodb-java";
    private final String id;

    ReusableNetwork() {
        id = ensureNetwork();
    }

    @Override
    public String getId() {
        return id;
    }

    public String getName() {
        return NAME;
    }

    @Override
    public void close() {

    }

    @Override
    public Statement apply(Statement base, Description description) {
        throw new UnsupportedOperationException();
    }

    private String ensureNetwork() {
        return DockerClientFactory.lazyClient()
                .listNetworksCmd()
                .withNameFilter(NAME)
                .exec()
                .stream()
                .findAny()
                .map(com.github.dockerjava.api.model.Network::getId)
                .orElseGet(this::createNetwork);
    }

    private String createNetwork() {
        return DockerClientFactory.lazyClient()
                .createNetworkCmd()
                .withName(NAME)
                .exec()
                .getId();
    }

}
