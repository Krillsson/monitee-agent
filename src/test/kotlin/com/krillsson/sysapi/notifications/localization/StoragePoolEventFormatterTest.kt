package com.krillsson.sysapi.notifications.localization

import com.krillsson.sysapi.storagepool.StoragePoolService
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

class StoragePoolEventFormatterTest {

    private val storagePoolService: StoragePoolService = mockk()
    private val formatter = StoragePoolEventFormatter(storagePoolService)

    private val poolId = "md:589f91b1-983e-d567-be4d-1cac8ce4abed"

    @Test
    fun `uses the pool's name when it is known`() {
        // Given
        every { storagePoolService.displayName(poolId) } returns "md5"

        // When
        val ongoing = formatter.formatOngoingDescription(poolId, "unavailable")
        val resolved = formatter.formatResolvedDescription(poolId, "online", "5 minutes")

        // Then
        ongoing shouldBe "Pool md5 is unavailable"
        resolved shouldBe "Pool md5 is back to online after 5 minutes"
    }

    @Test
    fun `falls back to the pool id when its name is unknown`() {
        // Given
        every { storagePoolService.displayName(poolId) } returns null

        // When
        val ongoing = formatter.formatOngoingDescription(poolId, "unavailable")

        // Then
        ongoing shouldBe "Pool $poolId is unavailable"
    }
}
