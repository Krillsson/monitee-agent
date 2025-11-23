package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerMetrics
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.webservicecheck.WebServerCheckHistoryEntry
import com.krillsson.sysapi.ups.UpsDevice

class MonitorInput(
    val load: SystemLoad,
    val containers: List<Container>,
    val containerStats: List<ContainerMetrics>,
    val webServerChecks: List<WebServerCheckHistoryEntry>,
    val upsDeviceMetrics: List<UpsDevice.Metrics>,
)