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

package utils;


import com.arangodb.next.communication.ArangoTopology;
import com.arangodb.next.connection.AuthenticationMethod;
import com.arangodb.next.connection.HostDescription;
import deployments.ArangoVersion;
import deployments.ImmutableArangoVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Michele Rastelli
 */
public enum TestUtils {
    INSTANCE;

    private static final String DEFAULT_DOCKER_IMAGE = "docker.io/arangodb/arangodb:3.6.2";

    private final Logger log;
    private final String arangoLicenseKey;
    private final String testDockerImage;
    private final ArangoVersion testArangodbVersion;
    private final boolean testContainersReuse;
    private final Set<HostDescription> hosts;
    private final AuthenticationMethod authentication;
    private final ArangoTopology topology;

    TestUtils() {
        log = LoggerFactory.getLogger(TestUtils.class);

        arangoLicenseKey = readArangoLicenseKey();
        log.info("Using arango license key: {}", arangoLicenseKey.replaceAll(".", "*"));

        testDockerImage = readTestDockerImage();
        log.info("Using docker image: {}", testDockerImage);

        testArangodbVersion = readTestArangodbVersion();
        log.info("Using version: {}", testArangodbVersion);

        testContainersReuse = readTestcontainersReuseEnable();
        log.info("Using testcontainers reuse: {}", testContainersReuse);

        hosts = readHosts();
        if (hosts != null) {
            log.info("Using hosts: {}", hosts);

            authentication = readAuthentication();
            log.info("Using authentication: {}", authentication);

            topology = readTopology();
            log.info("Using topology: {}", topology);
        } else {
            authentication = null;
            topology = null;
        }
    }

    private String readArangoLicenseKey() {
        String arangoLicenseKeyFromProperties = System.getProperty("arango.license.key");
        return arangoLicenseKeyFromProperties != null ? arangoLicenseKeyFromProperties : "";
    }

    private String readTestDockerImage() {
        String dockerImageFromProperties = System.getProperty("test.docker.image");
        return dockerImageFromProperties != null ? dockerImageFromProperties : DEFAULT_DOCKER_IMAGE;
    }

    private ArangoVersion readTestArangodbVersion() {
        String versionFromProperties = System.getProperty("test.arangodb.version");
        String version = versionFromProperties != null ? versionFromProperties :
                testDockerImage
                        .split(":")[1]  // docker image version
                        .split("-")[0]; // ignore alpha suffix
        String[] parts = version.split("\\.");
        return ImmutableArangoVersion.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    private boolean readTestcontainersReuseEnable() {
        return Boolean.parseBoolean(System.getProperty("testcontainers.reuse.enable"));
    }

    private Set<HostDescription> readHosts() {
        String prop = System.getProperty("test.arangodb.hosts");
        if (prop == null) {
            return null;
        }
        return Arrays.stream(prop.split(","))
                .map(it -> it.split(":"))
                .map(it -> HostDescription.of(it[0], Integer.parseInt(it[1])))
                .collect(Collectors.toSet());
    }

    private AuthenticationMethod readAuthentication() {
        String[] parts = System.getProperty("test.arangodb.authentication").split(":");
        return AuthenticationMethod.ofBasic(parts[0], parts[1]);
    }

    private ArangoTopology readTopology() {
        return ArangoTopology.valueOf(System.getProperty("test.arangodb.topology"));
    }

    public String getArangoLicenseKey() {
        return arangoLicenseKey;
    }

    public String getTestDockerImage() {
        return testDockerImage;
    }

    public ArangoVersion getTestArangodbVersion() {
        return testArangodbVersion;
    }

    public boolean isTestContainersReuse() {
        return testContainersReuse;
    }


}
