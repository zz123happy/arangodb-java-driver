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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Michele Rastelli
 */
class ContainerUtils {

    private static final String DEFAULT_DOCKER_IMAGE = "docker.io/arangodb/arangodb:3.5.3";
    private static final Logger log = LoggerFactory.getLogger(ContainerUtils.class);

    static String getImage() {
        String dockerImageFromProperties = System.getProperty("test.docker.image");
        String dockerImage = dockerImageFromProperties != null ? dockerImageFromProperties : DEFAULT_DOCKER_IMAGE;
        log.info("Using docker image: {}", dockerImage);
        return dockerImage;
    }

    static String getLicenseKey() {
        String arangoLicenseKeyFromProperties = System.getProperty("arango.license.key");
        String arangoLicenseKey = arangoLicenseKeyFromProperties != null ? arangoLicenseKeyFromProperties : "";
        log.info("Using arango license key: {}", arangoLicenseKey.replaceAll(".", "*"));
        return arangoLicenseKey;
    }
}
