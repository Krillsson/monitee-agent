package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty

data class GraphQLPlayGroundConfiguration(
    @JsonProperty var enabled: Boolean
)