package com.krillsson.sysapi.storagepool

import com.krillsson.sysapi.config.StoragePoolConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class StoragePoolServiceTest {

    private class FakeProvider(var isSupported: Boolean, private val pools: List<StoragePool>) : StoragePoolProvider {
        override fun supported(): Boolean = isSupported
        override fun pools(): List<StoragePool> = pools
    }

    private fun service(provider: StoragePoolProvider, enabled: Boolean = true) = StoragePoolService(
        mockk<YAMLConfigFile> { every { storagePools } returns StoragePoolConfiguration(enabled = enabled) },
        listOf(provider)
    )

    @Test
    fun `picks up a backend that becomes supported after startup`() {
        // Given
        val pool = storagePool()
        val provider = FakeProvider(isSupported = false, pools = listOf(pool))
        val service = service(provider)
        service.pools().shouldBeEmpty()

        // When
        provider.isSupported = true

        // Then
        service.status() shouldBe StoragePoolService.Status.Available
        service.pools() shouldBe listOf(pool)
    }

    @Test
    fun `reports nothing while disabled even with a supported backend`() {
        // Given
        val service = service(FakeProvider(isSupported = true, pools = listOf(storagePool())), enabled = false)

        // When / Then
        service.status().shouldBeInstanceOf<StoragePoolService.Status.Disabled>()
        service.pools().shouldBeEmpty()
    }
}
