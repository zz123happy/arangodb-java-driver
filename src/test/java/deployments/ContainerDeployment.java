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

import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.HostDescription;
import org.testcontainers.lifecycle.Startable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author Michele Rastelli
 */
public interface ContainerDeployment extends Startable {

    static ContainerDeployment ofCluster(int dbServers, int coordinators) {
        return new ClusterDeployment(dbServers, coordinators);
    }

    static ContainerDeployment ofSingleServerWithSsl() {
        return new SingleServerSslDeployment();
    }

    default String getImage() {
        return ContainerUtils.getImage();
    }

    List<HostDescription> getHosts();

    default String getUser() {
        return "root";
    }

    default String getPassword() {
        return "test";
    }

    default AuthenticationMethod getAuthentication() {
        return AuthenticationMethod.ofBasic(getUser(), getPassword());
    }

    CompletableFuture<? extends ContainerDeployment> asyncStart();

    CompletableFuture<ContainerDeployment> asyncStop();

    @Override
    default void start() {
        asyncStart().join();
    }

    @Override
    default void stop() {
        asyncStop().join();
    }

}
