package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.gpu.GpuLoad
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toFractionalValue
import java.util.*

class GpuUtilizationMonitor(override val id: UUID, override val config: MonitorConfig<MonitoredValue.FractionalValue>) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, monitoredItemId ->
            value(load.gpuLoads, monitoredItemId)
        }

        fun value(gpuLoads: List<GpuLoad>, monitoredItemId: String?): MonitoredValue.FractionalValue? {
            return gpuLoads.firstOrNull { it.id == monitoredItemId }?.coreLoad?.toFractionalValue()
        }
    }

    override val type: Type = Type.GPU_UTILIZATION

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, config.monitoredItemId)

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.FractionalValue? {
        return MonitoredValue.FractionalValue(100f)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}
