package com.krillsson.sysapi.core.history

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object HistoryBuckets {

    const val FIVE_MINUTES_IN_SECONDS = 300L

    fun startOfFiveMinutes(instant: Instant): Instant =
        Instant.ofEpochSecond(
            Math.floorDiv(instant.epochSecond, FIVE_MINUTES_IN_SECONDS) * FIVE_MINUTES_IN_SECONDS
        )

    fun startOfHour(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        instant.atZone(zone).truncatedTo(ChronoUnit.HOURS).toInstant()

    fun startOfDay(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        instant.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

    fun endOfFiveMinutes(bucketStart: Instant): Instant =
        bucketStart.plusSeconds(FIVE_MINUTES_IN_SECONDS)

    fun endOfHour(bucketStart: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        bucketStart.atZone(zone).plusHours(1).toInstant()

    fun endOfDay(bucketStart: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
        bucketStart.atZone(zone).plusDays(1).toInstant()
}
