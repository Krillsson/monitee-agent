package com.krillsson.sysapi.storagepool.zfs

import com.krillsson.sysapi.storagepool.StoragePoolDataSource
import com.krillsson.sysapi.storagepool.StoragePoolKind
import com.krillsson.sysapi.storagepool.StoragePoolState
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ZfsProviderTest {

    @ParameterizedTest
    @CsvSource(
        "ONLINE, ONLINE",
        "DEGRADED, DEGRADED",
        "FAULTED, FAULTED",
        "SUSPENDED, FAULTED",
        "SPLIT, FAULTED",
        "UNAVAIL, UNAVAIL",
        "OFFLINE, UNAVAIL",
        "REMOVED, UNAVAIL",
        "TRANSITIONING, UNKNOWN",
        "UNKNOWN, UNKNOWN"
    )
    fun `maps the kstat pool state onto the pool state`(kstatState: String, expected: StoragePoolState) {
        // Given / When
        val state = ZfsProvider.deriveState(kstatState)

        // Then
        state shouldBe expected
    }

    @Test
    fun `reports a pool by name with only its state`() {
        // Given
        val reader = mockk<ZfsKstatReader>()
        every { reader.exists() } returns true
        every { reader.readPools() } returns listOf(ZfsKstatPool("tank", "DEGRADED"))

        // When
        val pool = ZfsProvider(reader).pools().single()

        // Then
        pool.id shouldBe "zfs:tank"
        pool.name shouldBe "tank"
        pool.kind shouldBe StoragePoolKind.ZFS
        pool.state shouldBe StoragePoolState.DEGRADED
        pool.statusMessage shouldBe "DEGRADED"
        pool.devices.shouldBeEmpty()
        pool.dataSource shouldBe StoragePoolDataSource.KERNEL_INTERFACE
    }
}
