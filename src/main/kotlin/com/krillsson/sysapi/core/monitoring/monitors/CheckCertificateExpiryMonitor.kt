package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.check.certificate
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorInput
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.core.monitoring.toNumericalValue
import java.time.Clock
import java.util.*

class CheckCertificateExpiryMonitor(
    override val id: UUID,
    override val config: MonitorConfig<MonitoredValue.NumericalValue>,
    private val clock: Clock = Clock.systemUTC()
) : Monitor<MonitoredValue.NumericalValue>() {
    companion object {
        const val LONGEST_TRUSTED_LIFETIME_DAYS = 398L
    }

    override val type: Type = Type.CHECK_CERTIFICATE_EXPIRY

    override fun selectValue(event: MonitorInput): MonitoredValue.NumericalValue? {
        val result = event.checkCertificateResults.firstOrNull { it.checkId.toString() == config.monitoredItemId }
        return result?.certificate()?.daysUntilExpiry(clock.instant())?.toNumericalValue()
    }

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue.NumericalValue =
        LONGEST_TRUSTED_LIFETIME_DAYS.toNumericalValue()

    override fun isPastThreshold(value: MonitoredValue.NumericalValue, threshold: MonitoredValue.NumericalValue): Boolean {
        return value < threshold
    }
}
