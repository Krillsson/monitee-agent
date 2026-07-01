package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import java.util.*

class GpuTemperatureMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.NumericalValue>) :
    Monitor<MonitoredValue.NumericalValue>() {

    companion object {
        val selector: NumericalValueSelector = { load, monitoredItemId ->
            value(load.gpuLoads, monitoredItemId)
        }

        fun value(gpuLoads: List<GpuLoad>, monitoredItemId: String?): MonitoredValue.NumericalValue? {
            return gpuLoads.firstOrNull { it.id == monitoredItemId }?.health?.temperature?.toNumericalValue()
        }
    }

    override val type: Type = Type.GPU_TEMPERATURE

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue? {
        return MonitoredValue.NumericalValue(120)
    }

    override fun isPastThreshold(value: MonitoredValue.NumericalValue): Boolean {
        return value > config.threshold
    }
}
