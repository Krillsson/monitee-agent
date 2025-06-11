package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty

data class UpdateCheckConfiguration(
    @JsonProperty val enabled: Boolean = true,
    @JsonProperty val address: String = "https://api.github.com",
    @JsonProperty val user: String = "krillsson",
    @JsonProperty val repo: String = "monitee-agent",
)