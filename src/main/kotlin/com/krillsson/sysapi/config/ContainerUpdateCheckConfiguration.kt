package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty

data class ContainerUpdateCheckConfiguration(
    @JsonProperty val enabled: Boolean = true,
    @JsonProperty val notify: Boolean = true,
    @JsonProperty val notifyStyle: ContainerUpdateNotifyStyle = ContainerUpdateNotifyStyle.DAILY_DIGEST,
    @JsonProperty val digestAtHour: Int = 12,
    @JsonProperty val intervalMinutes: Long = 60,
    @JsonProperty val excludeContainers: List<String> = emptyList(),
    @JsonProperty val registries: List<RegistryConfiguration> = emptyList()
)

enum class ContainerUpdateNotifyStyle {
    DAILY_DIGEST,
    EVERY_CONTAINER
}

data class RegistryConfiguration(
    @JsonProperty val host: String,
    @JsonProperty val username: String,
    @JsonProperty val password: String
)
