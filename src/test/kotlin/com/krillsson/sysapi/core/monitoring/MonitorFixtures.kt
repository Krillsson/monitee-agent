package com.krillsson.sysapi.core.monitoring

import com.krillsson.sysapi.smart.HealthStatus
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

val MONITOR_ID: UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
const val MONITORED_ITEM_ID = "sda"

val START_OF_TIME: Instant = Instant.parse("2026-01-01T12:00:00Z")

class MutableClock(private var now: Instant = START_OF_TIME) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

class TestMonitor(
    override val id: UUID = MONITOR_ID,
    override val type: Monitor.Type = Monitor.Type.CPU_TEMP,
    override val config: MonitorConfig<MonitoredValue>
) : Monitor<MonitoredValue>() {
    override fun selectValue(event: MonitorInput): MonitoredValue? = null

    override fun maxValue(input: MonitorMaxValueInput): MonitoredValue? = null

    override fun isPastThreshold(value: MonitoredValue, threshold: MonitoredValue): Boolean = value != threshold
}

fun testMonitor(
    threshold: MonitoredValue = 80L.toNumericalValue(),
    inertia: Duration = Duration.ofMinutes(5),
    type: Monitor.Type = Monitor.Type.CPU_TEMP,
    monitoredItemId: String? = MONITORED_ITEM_ID,
    warningThreshold: MonitoredValue? = null,
    id: UUID = MONITOR_ID
) = TestMonitor(id, type, MonitorConfig(monitoredItemId, threshold, inertia, warningThreshold))

data class ValuesForKind(
    val monitorType: Monitor.Type,
    val threshold: MonitoredValue,
    val breaching: MonitoredValue,
    val recovered: MonitoredValue
)

fun valuesFor(valueType: Monitor.ValueType): ValuesForKind = when (valueType) {
    Monitor.ValueType.Numerical -> ValuesForKind(
        Monitor.Type.CPU_TEMP,
        80L.toNumericalValue(),
        95L.toNumericalValue(),
        60L.toNumericalValue()
    )

    Monitor.ValueType.Fractional -> ValuesForKind(
        Monitor.Type.CPU_LOAD,
        90f.toFractionalValue(),
        99.5f.toFractionalValue(),
        12.5f.toFractionalValue()
    )

    Monitor.ValueType.Conditional -> ValuesForKind(
        Monitor.Type.NETWORK_UP,
        true.toConditionalValue(),
        false.toConditionalValue(),
        true.toConditionalValue()
    )

    Monitor.ValueType.Enum -> ValuesForKind(
        Monitor.Type.DISK_SMART_HEALTH,
        HealthStatus.WARNING.toEnumValue(),
        HealthStatus.FAILING.toEnumValue(),
        HealthStatus.HEALTHY.toEnumValue()
    )
}
