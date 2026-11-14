package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.unraid.UnraidClient.Result
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

object UnraidConverter {

    const val VMS_DISABLED_REASON = "VMs are disabled in Unraid (Settings → VM Manager)"
    private const val VMS_NOT_AVAILABLE = "VMs are not available"
    private const val SET_STATE_FAILED = "Failed to set VM state:"
    private const val NO_VMS_FIELD = "Cannot query field \"vms\""

    fun vms(domains: List<UnraidApi.VmDomain>): List<UnraidVm> = domains.mapNotNull { vm(it) }

    fun vm(domain: UnraidApi.VmDomain): UnraidVm? =
        domain.id?.substringAfterLast(':')?.trim()?.takeIf { it.isNotEmpty() }?.let { id ->
            UnraidVm(
                id = id,
                name = domain.name?.trim()?.takeIf { it.isNotEmpty() } ?: id,
                state = state(domain.state)
            )
        }

    fun state(value: String?): UnraidVmState =
        UnraidVmState.entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: UnraidVmState.NOSTATE

    fun vmsDisabled(result: Result.Failure): Boolean =
        result is Result.GraphQlErrors && result.messages.any { it.contains(VMS_NOT_AVAILABLE, ignoreCase = true) }

    fun isPermanent(result: Result.Failure): Boolean =
        result is Result.Unauthorized || result is Result.Forbidden || noVmSupport(result)

    fun failureReason(result: Result.Failure, config: UnraidConfiguration): String {
        val address = address(config)
        return when (result) {
            is Result.Unauthorized ->
                "Unraid rejected the API key (${result.reason}). Check unraid.apiKey, and whether the key still exists"

            is Result.Forbidden -> "The API key lacks VMS read permission (${result.reason}). Give it vms:read_any"
            is Result.GraphQlErrors -> when {
                noVmSupport(result) -> "This Unraid version's API has no VM support. Unraid 7.2 or later is needed"
                else -> "Unraid answered with an error: ${result.messages.joinToString("; ").ifBlank { "no message" }}"
            }

            is Result.Redirected ->
                "Unraid at $address redirects to ${result.location ?: "another address"}. Set unraid.scheme, unraid.host and unraid.port to where it points"

            is Result.Failed -> "Unraid answered ${result.status}: ${result.reason}"
            is Result.TransportFailure -> when (result.error) {
                is SSLHandshakeException, is SSLPeerUnverifiedException ->
                    "The certificate of $address is not trusted (${result.error.message}). Set unraid.caCertificatePath to the certificate the webGUI uses, or set unraid.verifyTls to false"

                else -> "Could not reach Unraid at $address: ${result.error.message ?: result.error::class.java.simpleName}"
            }
        }
    }

    fun commandFailureReason(result: Result.Failure, config: UnraidConfiguration): String = when (result) {
        is Result.Forbidden -> "The API key lacks VMS update permission (${result.reason}). Give it vms:update_any"
        is Result.GraphQlErrors -> result.messages
            .map { it.removePrefix(SET_STATE_FAILED).trim() }
            .filter { it.isNotEmpty() }
            .joinToString("; ")
            .ifEmpty { "Unraid answered with an error" }

        else -> failureReason(result, config)
    }

    fun address(config: UnraidConfiguration): String = "${config.scheme}://${config.host}:${config.port}"

    private fun noVmSupport(result: Result.Failure): Boolean =
        result is Result.GraphQlErrors && result.messages.any { it.contains(NO_VMS_FIELD) }
}
