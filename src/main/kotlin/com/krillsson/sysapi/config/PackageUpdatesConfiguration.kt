package com.krillsson.sysapi.config

import com.fasterxml.jackson.annotation.JsonProperty

data class PackageUpdatesConfiguration(
    @JsonProperty val enabled: Boolean = true,
    @JsonProperty val notify: Boolean = true,
    @JsonProperty val notifyStyle: PackageUpdateNotifyStyle = PackageUpdateNotifyStyle.DAILY_DIGEST,
    @JsonProperty val digestAtHour: Int = 12,
    @JsonProperty val intervalHours: Long = 6
)

enum class PackageUpdateNotifyStyle {
    DAILY_DIGEST,
    IMMEDIATELY
}
