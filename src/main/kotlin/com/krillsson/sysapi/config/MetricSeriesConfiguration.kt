package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

data class MetricSeriesConfiguration(
        @JsonProperty val sampling: MetricSamplingConfiguration = MetricSamplingConfiguration(),
        @JsonProperty val raw: RetentionConfiguration = RetentionConfiguration(12, ChronoUnit.HOURS),
        @JsonProperty val fiveMinute: RetentionConfiguration = RetentionConfiguration(72, ChronoUnit.HOURS),
        @JsonProperty val hourly: RetentionConfiguration = RetentionConfiguration(90, ChronoUnit.DAYS),
        @JsonProperty val daily: RetentionConfiguration = RetentionConfiguration(730, ChronoUnit.DAYS)
)

data class MetricSamplingConfiguration(
        @JsonProperty val fast: IntervalConfiguration = IntervalConfiguration(60, TimeUnit.SECONDS),
        @JsonProperty val slow: IntervalConfiguration = IntervalConfiguration(5, TimeUnit.MINUTES)
)

data class IntervalConfiguration(
        @JsonProperty val interval: Long,
        @JsonProperty val unit: TimeUnit
)
