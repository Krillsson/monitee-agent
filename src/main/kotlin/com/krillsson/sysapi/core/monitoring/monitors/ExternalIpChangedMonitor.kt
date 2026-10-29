package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toConditionalValue
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorInput
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*

class ExternalIpChangedMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.ConditionalValue>,
    private val clock: Clock
) : Monitor<MonitoredValue.ConditionalValue>() {

    companion object {
        val holdChangeBeyondInertia: Duration = Duration.ofMinutes(1)

        val selector: ConditionalValueSelector = { load, _ ->
            val connectivity = load.connectivity
            value(connectivity)
        }

        fun value(connectivity: Connectivity): MonitoredValue.ConditionalValue? {
            val externalIp = connectivity.externalIp
            val previousExternalIp = connectivity.previousExternalIp

            return if (externalIp == null || previousExternalIp == null) {
                true.toConditionalValue()
            } else if (externalIp != previousExternalIp) {
                false.toConditionalValue()
            } else {
                true.toConditionalValue()
            }
        }
    }

    private var reportedIp: String? = null
    private var changedTo: String? = null
    private var changedAt: Instant? = null

    override val type: Type = Type.EXTERNAL_IP_CHANGED

    override fun selectValue(event: MonitorInput): MonitoredValue.ConditionalValue =
        unchanged(event.load.connectivity).toConditionalValue()

    private fun unchanged(connectivity: Connectivity): Boolean {
        val externalIp = connectivity.externalIp ?: return true
        val reported = reportedIp ?: (connectivity.previousExternalIp ?: externalIp).also { reportedIp = it }
        if (externalIp == reported) {
            clearChange()
            return true
        }
        val now = clock.instant()
        if (externalIp != changedTo) {
            changedTo = externalIp
            changedAt = now
        }
        if (Duration.between(changedAt, now) > config.inertia.plus(holdChangeBeyondInertia)) {
            reportedIp = externalIp
            clearChange()
            return true
        }
        return false
    }

    private fun clearChange() {
        changedTo = null
        changedAt = null
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.ConditionalValue? {
        return MonitoredValue.ConditionalValue(true)
    }

    override fun isPastThreshold(value: MonitoredValue.ConditionalValue, threshold: MonitoredValue.ConditionalValue): Boolean {
        return !value.value
    }
}
