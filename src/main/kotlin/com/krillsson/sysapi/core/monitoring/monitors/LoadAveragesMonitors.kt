package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.cpu.CpuLoad
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.domain.system.SystemInfo
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.util.*

class LoadAverageMonitorOneMinute(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.FractionalValue>
) : Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, _ ->
            val cpuLoad = load.cpuLoad
            value(cpuLoad)
        }

        fun value(cpuLoad: CpuLoad) =
            MonitoredValue.FractionalValue(cpuLoad.loadAverages.oneMinute.toFloat())

        val maxValueSelector: MaxValueFractionalSelector = { info, _ ->
            MonitoredValue.FractionalValue(info.cpuInfo.centralProcessor.logicalProcessorCount.toFloat())
        }
    }

    override val type: Type = Type.LOAD_AVERAGE_ONE_MINUTE

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.FractionalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}

class LoadAverageMonitorFiveMinutes(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.FractionalValue>
) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, _ ->
            val cpuLoad = load.cpuLoad
            value(cpuLoad)
        }

        fun value(cpuLoad: CpuLoad) =
            MonitoredValue.FractionalValue(cpuLoad.loadAverages.fiveMinutes.toFloat())

        val maxValueSelector: MaxValueFractionalSelector = { info, _ ->
            MonitoredValue.FractionalValue(info.cpuInfo.centralProcessor.logicalProcessorCount.toFloat())
        }
    }

    override val type: Type = Type.LOAD_AVERAGE_FIVE_MINUTES

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.FractionalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}

class LoadAverageMonitorFifteenMinutes(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.FractionalValue>
) :
    Monitor<MonitoredValue.FractionalValue>() {

    companion object {
        val selector: FractionalValueSelector = { load, _ ->
            val cpuLoad = load.cpuLoad
            value(cpuLoad)
        }

        fun value(cpuLoad: CpuLoad) =
            MonitoredValue.FractionalValue(cpuLoad.loadAverages.fifteenMinutes.toFloat())

        val maxValueSelector: MaxValueFractionalSelector = { info, _ ->
            MonitoredValue.FractionalValue(info.cpuInfo.centralProcessor.logicalProcessorCount.toFloat())
        }
    }

    override val type: Type = Type.LOAD_AVERAGE_FIFTEEN_MINUTES

    override fun selectValue(event: MonitorInput): MonitoredValue.FractionalValue? =
        selector(event.load, null)

    override fun maxValue(info: SystemInfo): MonitoredValue.FractionalValue? {
        return maxValueSelector(info, null)
    }

    override fun isPastThreshold(value: MonitoredValue.FractionalValue): Boolean {
        return value > config.threshold
    }
}