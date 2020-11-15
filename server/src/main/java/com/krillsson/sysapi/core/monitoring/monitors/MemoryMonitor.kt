package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.monitor.MonitorConfig
import com.krillsson.sysapi.core.domain.system.SystemLoad
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorType
import java.util.*

class MemoryMonitor(override val id: UUID, override val config: MonitorConfig) : Monitor() {
    override val type: MonitorType = MonitorType.MEMORY_SPACE

    override fun selectValue(load: SystemLoad): Double {
        return load.memory.availableBytes.toDouble()
    }

    override fun isPastThreshold(value: Double): Boolean {
        return value < config.threshold
    }
}