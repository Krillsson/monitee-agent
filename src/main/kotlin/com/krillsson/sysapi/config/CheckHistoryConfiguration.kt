package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.temporal.ChronoUnit

data class CheckHistoryConfiguration(
        @JsonProperty val raw: RetentionConfiguration = RetentionConfiguration(48, ChronoUnit.HOURS),
        @JsonProperty val hourly: RetentionConfiguration = RetentionConfiguration(90, ChronoUnit.DAYS),
        @JsonProperty val daily: RetentionConfiguration = RetentionConfiguration(730, ChronoUnit.DAYS)
)

data class RetentionConfiguration(
        @JsonProperty val olderThan: Long,
        @JsonProperty val unit: ChronoUnit
)
