package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.smart.DeviceHealth
import com.krillsson.sysapi.smart.HealthStatus
import com.krillsson.sysapi.smart.SmartData
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
@SchemaMapping(typeName = "DiskMetrics")
class DiskMetricsSmartResolver {

    @SchemaMapping
    fun smartData(driveLoad: com.krillsson.sysapi.core.domain.disk.DiskLoad): SmartData? = driveLoad.smartData

    @SchemaMapping
    fun health(driveLoad: com.krillsson.sysapi.core.domain.disk.DiskLoad): DeviceHealth? = driveLoad.health
}

@Controller
@SchemaMapping(typeName = "DeviceHealth")
class DeviceHealthResolver {

    @SchemaMapping
    fun status(deviceHealth: DeviceHealth): HealthStatus = deviceHealth.status

    @SchemaMapping
    fun messages(deviceHealth: DeviceHealth): List<String> = deviceHealth.messages
}
