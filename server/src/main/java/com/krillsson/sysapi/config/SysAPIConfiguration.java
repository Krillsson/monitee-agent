/*
 * Sys-Api (https://github.com/Krillsson/sys-api)
 *
 * Copyright 2017 Christian Jensen / Krillsson
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
 * Maintainers:
 * contact[at]christian-jensen[dot]se
 */
package com.krillsson.sysapi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smoketurner.dropwizard.graphql.GraphQLFactory;
import io.dropwizard.Configuration;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

public class SysAPIConfiguration extends Configuration {

    @Valid
    @NotNull
    public final GraphQLFactory graphql = new GraphQLFactory();

    @Valid
    @NotNull
    @JsonProperty
    private UserConfiguration user;

    @Valid
    @NotNull
    @JsonProperty
    private MetricsConfiguration metricsConfig;

    @Valid
    @JsonProperty
    private WindowsConfiguration windows;

    @Valid
    @JsonProperty
    private GraphQLPlayGroundConfiguration graphQLPlayGround;

    @Valid
    @JsonProperty
    private DockerConfiguration docker;

    @Valid
    @JsonProperty
    private boolean forwardHttps;

    public MetricsConfiguration metrics() {
        return metricsConfig;
    }

    public UserConfiguration user() {
        return user;
    }

    public WindowsConfiguration windows() {
        return windows;
    }

    public boolean forwardHttps() {
        return forwardHttps;
    }

    public DockerConfiguration docker() {
        return docker;
    }

    @JsonProperty
    public GraphQLFactory getGraphQLFactory() {
        return graphql;
    }

    @JsonProperty
    public GraphQLPlayGroundConfiguration getGraphQLPlayGround() {
        return graphQLPlayGround;
    }
}
