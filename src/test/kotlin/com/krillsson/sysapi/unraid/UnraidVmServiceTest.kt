package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.unraid.UnraidClient.Result
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant

class UnraidVmServiceTest {

    private val client = mockk<UnraidClient>()
    private val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = mockk<Clock> { every { instant() } returns now }

    private val windows = UnraidApi.VmDomain("f3a9c1e2d4b5:$WINDOWS_UUID", "Windows 11", "RUNNING")
    private val homeAssistant = UnraidApi.VmDomain("f3a9c1e2d4b5:$HOME_ASSISTANT_UUID", "home-assistant", "PAUSED")
    private val vmsNotAvailable = Result.GraphQlErrors(listOf("VMs are not available"), listOf("INTERNAL_SERVER_ERROR"))

    private fun service(configuration: UnraidConfiguration = configured()): UnraidVmService {
        val configFile = mockk<YAMLConfigFile> { every { unraid } returns configuration }
        return UnraidVmService(configFile, client, taskScheduler, clock)
    }

    private fun configured(pollIntervalSeconds: Long = 15) =
        UnraidConfiguration(enabled = true, apiKey = "a1b2c3", pollIntervalSeconds = pollIntervalSeconds)

    private fun answers(vararg results: Result<List<UnraidApi.VmDomain>>) {
        every { client.vmDomains() } returnsMany results.toList()
    }

    @Test
    fun `is disabled unless enabled in the configuration`() {
        // When
        val status = service(UnraidConfiguration()).status()

        // Then
        status shouldBe UnraidVmService.Status.Disabled
    }

    @Test
    fun `asks for an api key instead of polling when none is configured`() {
        // Given
        val service = service(UnraidConfiguration(enabled = true))

        // When
        service.start()

        // Then
        service.status() shouldBe UnraidVmService.Status.Unavailable("unraid.apiKey must be set in configuration.yml")
        verify(exactly = 0) { taskScheduler.scheduleAtFixedRate(any(), any<Duration>()) }
    }

    @Test
    fun `refuses a scheme other than http and https`() {
        // When
        val status = service(configured().copy(scheme = "ftp")).status()

        // Then
        status shouldBe UnraidVmService.Status.Unavailable("unraid.scheme must be http or https, not ftp")
    }

    @Test
    fun `polls no faster than every five seconds`() {
        // When
        service(configured(pollIntervalSeconds = 1)).start()

        // Then
        verify { taskScheduler.scheduleAtFixedRate(any(), Duration.ofSeconds(5)) }
    }

    @Test
    fun `is unavailable until the first poll has answered`() {
        // When
        val status = service().status()

        // Then
        status shouldBe UnraidVmService.Status.Unavailable("Waiting for the first answer from Unraid at http://localhost:80")
    }

    @Test
    fun `lists the vms by name with their bare uuid`() {
        // Given
        answers(Result.Ok(listOf(windows, homeAssistant)))
        val service = service()

        // When
        service.poll()

        // Then
        service.status() shouldBe UnraidVmService.Status.Available
        service.vms() shouldContainExactly listOf(
            UnraidVm(HOME_ASSISTANT_UUID, "home-assistant", UnraidVmState.PAUSED),
            UnraidVm(WINDOWS_UUID, "Windows 11", UnraidVmState.RUNNING)
        )
        service.snapshot().shouldNotBeNull().lastSuccessfulPoll shouldBe now
    }

    @Test
    fun `keeps the last answer through two failed polls and gives up on the third`() {
        // Given
        val failure = Result.TransportFailure(IOException("Connection refused"))
        answers(Result.Ok(listOf(windows)), failure, failure, failure)
        val service = service()
        service.poll()

        // When
        service.poll()
        service.poll()
        val afterTwo = service.status()
        service.poll()

        // Then
        afterTwo shouldBe UnraidVmService.Status.Available
        service.status() shouldBe UnraidVmService.Status.Unavailable("Could not reach Unraid at http://localhost:80: Connection refused")
        service.vms().shouldBeEmpty()
    }

    @Test
    fun `gives up at once when unraid rejects the key`() {
        // Given
        answers(Result.Ok(listOf(windows)), Result.Unauthorized("Unauthorized"))
        val service = service()
        service.poll()

        // When
        service.poll()

        // Then
        service.status() shouldBe UnraidVmService.Status.Unavailable(
            "Unraid rejected the API key (Unauthorized). Check unraid.apiKey, and whether the key still exists"
        )
    }

    @Test
    fun `recovers once unraid answers again`() {
        // Given
        answers(Result.Unauthorized("Unauthorized"), Result.Ok(listOf(windows)))
        val service = service()
        service.poll()

        // When
        service.poll()

        // Then
        service.status() shouldBe UnraidVmService.Status.Available
    }

    @Test
    fun `keeps a vm that is missing from one answer and drops it on the next`() {
        // Given
        answers(Result.Ok(listOf(windows, homeAssistant)), Result.Ok(listOf(windows)), Result.Ok(listOf(windows)))
        val service = service()
        service.poll()

        // When
        service.poll()
        val afterOne = service.vms().map { it.id }
        service.poll()

        // Then
        afterOne shouldContainExactly listOf(HOME_ASSISTANT_UUID, WINDOWS_UUID)
        service.vms().map { it.id } shouldContainExactly listOf(WINDOWS_UUID)
    }

    @Test
    fun `stays available with no vms while the vm manager is stopped, and says so`() {
        // Given
        answers(Result.Ok(listOf(windows)), vmsNotAvailable)
        val service = service()
        service.poll()

        // When
        service.poll()

        // Then
        service.status() shouldBe UnraidVmService.Status.Available
        service.vms().shouldBeEmpty()
        service.snapshot().shouldNotBeNull().vmsUnavailableReason shouldBe "VMs are disabled in Unraid (Settings → VM Manager)"
    }

    @Test
    fun `offers no vms to the monitors while the list could not be read`() {
        // Given
        answers(vmsNotAvailable)
        val unpolled = service()
        val stopped = service().also { it.poll() }

        // When
        val beforeFirstPoll = unpolled.monitorableVms()
        val whileStopped = stopped.monitorableVms()

        // Then
        beforeFirstPoll.shouldBeNull()
        whileStopped.shouldBeNull()
    }

    @Test
    fun `offers the vms to the monitors once they are listed`() {
        // Given
        answers(Result.Ok(emptyList()))
        val service = service()

        // When
        service.poll()

        // Then
        service.monitorableVms() shouldBe emptyList()
    }
}
