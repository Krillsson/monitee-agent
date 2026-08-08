package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class HistoryConfiguration(
        @JsonProperty val purging: HistoryPurgingConfiguration = HistoryPurgingConfiguration(14, ChronoUnit.DAYS, 1, TimeUnit.DAYS),
        @JsonProperty val interval: Long = 30,
        @JsonProperty val unit: TimeUnit = TimeUnit.MINUTES,
        @JsonProperty val checks: CheckHistoryConfiguration = CheckHistoryConfiguration()
)
