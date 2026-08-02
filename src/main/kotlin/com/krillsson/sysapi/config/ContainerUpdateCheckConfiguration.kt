package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty

data class ContainerUpdateCheckConfiguration(
    @JsonProperty val enabled: Boolean = true,
    @JsonProperty val notify: Boolean = true,
    @JsonProperty val intervalMinutes: Long = 60,
    @JsonProperty val excludeContainers: List<String> = emptyList(),
    @JsonProperty val registries: List<RegistryConfiguration> = emptyList()
)

data class RegistryConfiguration(
    @JsonProperty val host: String,
    @JsonProperty val username: String,
    @JsonProperty val password: String
)
