package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.cpu.CpuInfo
import com.krillsson.sysapi.core.domain.memory.MemoryInfo
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class StoragePoolHealthMonitorTest {

    private fun monitor(threshold: StoragePoolState) = StoragePoolHealthMonitor(
        UUID.randomUUID(),
        MonitorConfig(
            monitoredItemId = "md:abcd",
            threshold = MonitoredValue.EnumValue(threshold),
            inertia = Duration.ZERO
        )
    )

    @Test
    fun `does not fire while online`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.ONLINE)) shouldBe false
    }

    @Test
    fun `does not fire on an unknown reading even though it outranks online`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNKNOWN)) shouldBe false
    }

    @Test
    fun `fires once a pool becomes degraded`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.DEGRADED)) shouldBe true
    }

    @Test
    fun `fires once a pool becomes faulted or unavailable`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.FAULTED)) shouldBe true
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNAVAIL)) shouldBe true
    }

    @Test
    fun `a degraded threshold does not fire on unknown or online`() {
        // Given
        val degradedThreshold = monitor(StoragePoolState.DEGRADED)

        // When / Then
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.ONLINE)) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNKNOWN)) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.DEGRADED)) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.FAULTED)) shouldBe true
    }

    @Test
    fun `maxValue is the worst possible state`() {
        // Given
        val monitor = monitor(StoragePoolState.ONLINE)

        // When
        val maxValue = monitor.maxValue(
            MonitorMaxValueInput(
                cpuInfo = mockk<CpuInfo>(relaxed = true),
                memory = mockk<MemoryInfo>(relaxed = true),
                fileSystems = emptyList(),
                networkInterfaces = emptyList(),
                upsDevices = emptyList(),
                gpus = emptyList(),
                checks = emptyList()
            )
        )

        // Then
        maxValue shouldBe MonitoredValue.EnumValue(StoragePoolState.UNAVAIL)
    }
}
