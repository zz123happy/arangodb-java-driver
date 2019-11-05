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

package com.arangodb.next.connection;

import org.immutables.value.Value;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * @author Michele Rastelli
 */
public interface AuthenticationMethod {

    String getHttpAuthorizationHeader();

    List<Object> getVstAuthenticationMessage();

    static AuthenticationMethod jwt(final String jwt) {
        return ImmutableJwtAuthenticationMethod.of(jwt);
    }

    static AuthenticationMethod basic(final String user, final String password) {
        return ImmutableBasicAuthenticationMethod.of(user, password);
    }

    @Value.Immutable(builder = false)
    abstract class JwtAuthenticationMethod implements AuthenticationMethod {

        @Value.Parameter
        abstract String getJwt();

        @Override
        public String getHttpAuthorizationHeader() {
            return "Bearer " + getJwt();
        }

        @Override
        public List<Object> getVstAuthenticationMessage() {
            return Arrays.asList(
                    1,
                    1000,
                    "jwt",
                    getJwt()
            );
        }

    }

    @Value.Immutable(builder = false)
    abstract class BasicAuthenticationMethod implements AuthenticationMethod {

        @Value.Parameter(order = 1)
        abstract String getUser();

        @Value.Parameter(order = 2)
        abstract String getPassword();

        @Override
        public String getHttpAuthorizationHeader() {
            final String plainAuth = getUser() + ":" + getPassword();
            final String encodedAuth = Base64.getEncoder().encodeToString(plainAuth.getBytes());
            return "Basic " + encodedAuth;
        }

        @Override
        public List<Object> getVstAuthenticationMessage() {
            return Arrays.asList(
                    1,
                    1000,
                    "plain",
                    getUser(),
                    getPassword()
            );
        }

    }

}
