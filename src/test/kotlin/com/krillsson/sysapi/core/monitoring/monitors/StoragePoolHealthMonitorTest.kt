package com.krillsson.sysapi.core.monitoring.monitors

import com.krillsson.sysapi.core.domain.cpu.CpuInfo
import com.krillsson.sysapi.core.domain.memory.MemoryInfo
import com.krillsson.sysapi.core.monitoring.MonitorConfig
import com.krillsson.sysapi.core.monitoring.MonitorMaxValueInput
import com.krillsson.sysapi.core.monitoring.MonitoredValue
import com.krillsson.sysapi.storagepool.StoragePoolState
import com.krillsson.sysapi.storagepool.storagePool
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
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.ONLINE), onlineThreshold.config.threshold) shouldBe false
    }

    @Test
    fun `does not fire on an unknown reading even though it outranks online`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNKNOWN), onlineThreshold.config.threshold) shouldBe false
    }

    @Test
    fun `fires once a pool becomes degraded`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.DEGRADED), onlineThreshold.config.threshold) shouldBe true
    }

    @Test
    fun `reads a pool that has disappeared as unavailable so its monitor fires instead of being removed`() {
        // Given
        val pools = listOf(storagePool(id = "md:other"))

        // When
        val value = StoragePoolHealthMonitor.value(pools, "md:abcd")

        // Then
        value shouldBe MonitoredValue.EnumValue(StoragePoolState.UNAVAIL)
    }

    @Test
    fun `reads the state of a pool that is present`() {
        // Given
        val pools = listOf(storagePool(id = "md:abcd", state = StoragePoolState.DEGRADED))

        // When
        val value = StoragePoolHealthMonitor.value(pools, "md:abcd")

        // Then
        value shouldBe MonitoredValue.EnumValue(StoragePoolState.DEGRADED)
    }

    @Test
    fun `fires once a pool becomes faulted or unavailable`() {
        // Given
        val onlineThreshold = monitor(StoragePoolState.ONLINE)

        // When / Then
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.FAULTED), onlineThreshold.config.threshold) shouldBe true
        onlineThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNAVAIL), onlineThreshold.config.threshold) shouldBe true
    }

    @Test
    fun `a degraded threshold does not fire on unknown or online`() {
        // Given
        val degradedThreshold = monitor(StoragePoolState.DEGRADED)

        // When / Then
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.ONLINE), degradedThreshold.config.threshold) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.UNKNOWN), degradedThreshold.config.threshold) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.DEGRADED), degradedThreshold.config.threshold) shouldBe false
        degradedThreshold.isPastThreshold(MonitoredValue.EnumValue(StoragePoolState.FAULTED), degradedThreshold.config.threshold) shouldBe true
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
