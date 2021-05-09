package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorMetricQueryEvent
import com.krillsson.sysapi.core.monitoring.MonitorType
import java.util.*

class CpuTemperatureMonitor(override val id: UUID, override val config: MonitorConfig) : Monitor() {

    override val type: MonitorType = MonitorType.CPU_TEMP

    override fun selectValue(event: MonitorMetricQueryEvent): Double =
            event.load().cpuLoad.cpuHealth.temperatures.stream().findFirst().orElse(-1.0)

    override fun isPastThreshold(value: Double): Boolean {
        return value > config.threshold
    }
}