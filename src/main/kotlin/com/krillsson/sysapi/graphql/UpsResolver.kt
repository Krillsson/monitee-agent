package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.ups.UpsService
import com.krillsson.sysapi.ups.UpsDevice
import com.krillsson.sysapi.ups.UpsMetricsHistoryEntry
import com.krillsson.sysapi.ups.UpsMetricsHistoryRepository
import java.time.Instant
import org.springframework.graphql.data.method.annotation.Argument
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "UpsInfoAvailable")
class UpsResolver(
    val upsService: UpsService,
    val upsMetricsHistoryRepository: UpsMetricsHistoryRepository
) {

    @SchemaMapping
    fun upsDevices(): List<UpsDevice> {
        return upsService.upsDevices()
    }

    @SchemaMapping
    fun upsDeviceById(@Argument id: String): UpsDevice? {
        return upsService.upsDeviceByName(id)
    }

    @SchemaMapping
    fun metricsForUps(@Argument id: String): UpsDevice.Metrics? {
        return upsService.upsDeviceByName(id)?.metrics
    }

    @SchemaMapping
    fun upsMetricsHistoryBetweenTimestamps(
        @Argument id: String,
        @Argument from: Instant,
        @Argument to: Instant
    ): List<UpsMetricsHistoryEntry> {
        return upsMetricsHistoryRepository.getHistoryLimitedToDates(id, from, to)
    }
}