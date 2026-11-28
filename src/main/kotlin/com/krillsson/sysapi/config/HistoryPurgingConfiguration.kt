package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.temporal.ChronoUnit

data class HistoryPurgingConfiguration(
        @JsonProperty val olderThan: Long,
        @JsonProperty val unit: ChronoUnit
)
