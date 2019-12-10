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
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.lifecycle.Startable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * @author Michele Rastelli
 */
public abstract class ContainerDeployment implements Startable {

    public static ContainerDeployment ofSingleServerWithSsl() {
        return new SingleServerSslDeployment();
    }

    public static ContainerDeployment ofSingleServerNoAuth() {
        return new SingleServerNoAuthDeployment();
    }

    public static ContainerDeployment ofCluster(int dbServers, int coordinators) {
        return new ClusterDeployment(dbServers, coordinators);
    }

    @Override
    public void start() {
        try {
            asyncStart().join();
        } catch (CompletionException e) {
            e.printStackTrace();
            if (e.getCause() instanceof ContainerLaunchException) {
                System.out.println("Containers failed to start, retrying...");
                stop();
                start();
            } else {
                throw e;
            }
        }
    }

    @Override
    public void stop() {
        asyncStop().join();
    }

    public AuthenticationMethod getAuthentication() {
        return AuthenticationMethod.ofBasic(getUser(), getPassword());
    }

    public String getUser() {
        return "root";
    }

    public String getPassword() {
        return "test";
    }

    public String getBasicAuthentication() {
        return "Basic cm9vdDp0ZXN0";
    }

    public abstract List<HostDescription> getHosts();

    protected String getImage() {
        return ContainerUtils.getImage();
    }

    protected abstract CompletableFuture<? extends ContainerDeployment> asyncStart();

    protected abstract CompletableFuture<ContainerDeployment> asyncStop();

}
