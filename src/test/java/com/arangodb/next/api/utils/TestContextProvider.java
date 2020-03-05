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

package com.arangodb.next.api.utils;

import deployments.ContainerDeployment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;


/**
 * @author Michele Rastelli
 */
public enum TestContextProvider implements Supplier<List<TestContext>> {
    INSTANCE;

    private final List<TestContext> contexts;

    TestContextProvider() {
        List<ContainerDeployment> deployments = Arrays.asList(
                ContainerDeployment.ofSingleServer(),
//                ContainerDeployment.ofActiveFailover(3),
                ContainerDeployment.ofCluster(2, 2)
        );

        List<CompletableFuture<Void>> startingTasks = deployments.stream()
                .map(it -> CompletableFuture.runAsync(it::start))
                .collect(Collectors.toList());
        startingTasks.forEach(CompletableFuture::join);

        contexts = deployments.stream()
                .flatMap(TestContext::createContexts)
                .collect(Collectors.toList());
    }

    @Override
    public List<TestContext> get() {
        return contexts;
    }

}
