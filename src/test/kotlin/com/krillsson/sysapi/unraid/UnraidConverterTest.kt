package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.unraid.UnraidClient.Result
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import javax.net.ssl.SSLHandshakeException

class UnraidConverterTest {

    private val config = UnraidConfiguration(enabled = true, apiKey = "key")

    @ParameterizedTest
    @CsvSource(
        "f3a9c1e2d4b5:$WINDOWS_UUID",
        WINDOWS_UUID
    )
    fun `keeps the bare uuid whether or not the id carries the server prefix`(id: String) {
        // When
        val vm = UnraidConverter.vm(UnraidApi.VmDomain(id, "Windows 11", "RUNNING"))

        // Then
        vm shouldBe UnraidVm(WINDOWS_UUID, "Windows 11", UnraidVmState.RUNNING)
    }

    @Test
    fun `names a vm without a name after its uuid`() {
        // When
        val vms = UnraidConverter.vms(
            listOf(UnraidApi.VmDomain(HOME_ASSISTANT_UUID, null, "SHUTOFF"), UnraidApi.VmDomain(WINDOWS_UUID, " ", "SHUTOFF"))
        )

        // Then
        vms.map { it.name } shouldContainExactly listOf(HOME_ASSISTANT_UUID, WINDOWS_UUID)
    }

    @Test
    fun `leaves out a domain without an id`() {
        // When
        val vms = UnraidConverter.vms(listOf(UnraidApi.VmDomain(null, "ghost", "RUNNING")))

        // Then
        vms shouldBe emptyList()
    }

    @ParameterizedTest
    @CsvSource(
        "NOSTATE, NOSTATE",
        "RUNNING, RUNNING",
        "IDLE, IDLE",
        "PAUSED, PAUSED",
        "SHUTDOWN, SHUTDOWN",
        "SHUTOFF, SHUTOFF",
        "CRASHED, CRASHED",
        "PMSUSPENDED, PMSUSPENDED",
        "running, RUNNING",
        "BLOCKED, NOSTATE",
        "'', NOSTATE"
    )
    fun `reads the state unraid reports and falls back to no state`(value: String, expected: UnraidVmState) {
        // When
        val state = UnraidConverter.state(value)

        // Then
        state shouldBe expected
    }

    @Test
    fun `recognises the vm manager being stopped`() {
        // Given
        val stopped = Result.GraphQlErrors(listOf("VMs are not available"), listOf("INTERNAL_SERVER_ERROR"))
        val other = Result.GraphQlErrors(listOf("Something else"), emptyList())

        // When
        val recognised = listOf(UnraidConverter.vmsDisabled(stopped), UnraidConverter.vmsDisabled(other))

        // Then
        recognised shouldBe listOf(true, false)
    }

    @Test
    fun `gives up at once on failures another poll cannot fix`() {
        // Given
        val failures = listOf(
            Result.Unauthorized("Unauthorized"),
            Result.Forbidden("Forbidden resource"),
            Result.GraphQlErrors(listOf("Cannot query field \"vms\" on type \"Query\"."), emptyList()),
            Result.Failed(429, "Too Many Requests"),
            Result.TransportFailure(IOException("Connection refused"))
        )

        // When
        val permanent = failures.map { UnraidConverter.isPermanent(it) }

        // Then
        permanent shouldBe listOf(true, true, true, false, false)
    }

    @Test
    fun `explains each failure in terms of the configuration`() {
        // Given
        val failures = listOf(
            Result.Unauthorized("Unauthorized"),
            Result.Forbidden("Forbidden resource"),
            Result.GraphQlErrors(listOf("Cannot query field \"vms\" on type \"Query\"."), emptyList()),
            Result.Redirected(301, "https://localhost/graphql"),
            Result.Failed(429, "Too Many Requests"),
            Result.TransportFailure(SSLHandshakeException("PKIX path building failed")),
            Result.TransportFailure(IOException("Connection refused"))
        )

        // When
        val reasons = failures.map { UnraidConverter.failureReason(it, config) }

        // Then
        reasons shouldContainExactly listOf(
            "Unraid rejected the API key (Unauthorized). Check unraid.apiKey, and whether the key still exists",
            "The API key lacks VMS read permission (Forbidden resource). Give it vms:read_any",
            "This Unraid version's API has no VM support. Unraid 7.2 or later is needed",
            "Unraid at http://localhost:80 redirects to https://localhost/graphql. Set unraid.scheme, unraid.host and unraid.port to where it points",
            "Unraid answered 429: Too Many Requests",
            "The certificate of http://localhost:80 is not trusted (PKIX path building failed). Set unraid.caCertificatePath to the certificate the webGUI uses, or set unraid.verifyTls to false",
            "Could not reach Unraid at http://localhost:80: Connection refused"
        )
    }

    @Test
    fun `explains a failed command without unraid's set state prefix, and a missing update permission`() {
        // Given
        val failures = listOf(
            Result.GraphQlErrors(listOf("Failed to set VM state: Cannot pause VM in state SHUTOFF"), emptyList()),
            Result.GraphQlErrors(listOf("Failed to force stop VM: domain is not running"), emptyList()),
            Result.GraphQlErrors(emptyList(), listOf("INTERNAL_SERVER_ERROR")),
            Result.Forbidden("Forbidden resource"),
            Result.TransportFailure(IOException("timeout"))
        )

        // When
        val reasons = failures.map { UnraidConverter.commandFailureReason(it, config) }

        // Then
        reasons shouldContainExactly listOf(
            "Cannot pause VM in state SHUTOFF",
            "Failed to force stop VM: domain is not running",
            "Unraid answered with an error",
            "The API key lacks VMS update permission (Forbidden resource). Give it vms:update_any",
            "Could not reach Unraid at http://localhost:80: timeout"
        )
    }
}
