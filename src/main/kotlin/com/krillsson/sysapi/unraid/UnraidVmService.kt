package com.krillsson.sysapi.unraid

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.unraid.UnraidClient.Result
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import java.io.File
import java.time.Clock
import java.time.Duration

@Service
class UnraidVmService(
    configuration: YAMLConfigFile,
    private val client: UnraidClient,
    private val taskScheduler: TaskScheduler,
    private val clock: Clock
) {
    companion object {
        const val FAILURES_BEFORE_UNAVAILABLE = 3
        private const val MINIMUM_POLL_INTERVAL_SECONDS = 5L
        private val SCHEMES = setOf("http", "https")
    }

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val reason: String) : Status()
    }

    private val logger by logger()

    private val config = configuration.unraid

    private val pollInterval = Duration.ofSeconds(config.pollIntervalSeconds.coerceAtLeast(MINIMUM_POLL_INTERVAL_SECONDS))

    @Volatile
    private var snapshot: UnraidSnapshot? = null

    @Volatile
    private var consecutiveFailures = 0

    @Volatile
    private var lastFailure: String? = null

    private var missingVmIds = emptySet<String>()

    @PostConstruct
    fun start() {
        if (config.enabled && configurationError() == null) {
            if (config.scheme == "https" && !config.verifyTls) {
                logger.warn("unraid.verifyTls is false: the agent will accept any certificate from {}", UnraidConverter.address(config))
            }
            taskScheduler.scheduleAtFixedRate(this::poll, pollInterval)
        }
    }

    fun status(): Status {
        val configurationError = configurationError()
        return when {
            !config.enabled -> Status.Disabled
            configurationError != null -> Status.Unavailable(configurationError)
            snapshot == null || consecutiveFailures >= FAILURES_BEFORE_UNAVAILABLE -> Status.Unavailable(
                lastFailure ?: "Waiting for the first answer from Unraid at ${UnraidConverter.address(config)}"
            )

            else -> Status.Available
        }
    }

    fun snapshot(): UnraidSnapshot? = snapshot.takeIf { status() == Status.Available }

    fun vms(): List<UnraidVm> = snapshot()?.vms.orEmpty()

    fun vm(id: String): UnraidVm? = vms().firstOrNull { it.id == id }

    fun monitorableVms(): List<UnraidVm>? = snapshot()?.takeIf { it.vmsUnavailableReason == null }?.vms

    @Synchronized
    fun poll() {
        try {
            when (val result = client.vmDomains()) {
                is Result.Ok -> refresh(retainMissingOnce(UnraidConverter.vms(result.data)), vmsUnavailableReason = null)
                is Result.Failure -> when {
                    UnraidConverter.vmsDisabled(result) -> {
                        missingVmIds = emptySet()
                        refresh(emptyList(), UnraidConverter.VMS_DISABLED_REASON)
                    }

                    else -> recordFailure(result)
                }
            }
        } catch (exception: Exception) {
            logger.warn("Failed to poll Unraid", exception)
            consecutiveFailures++
            lastFailure = exception.message ?: exception::class.java.simpleName
        }
    }

    private fun refresh(vms: List<UnraidVm>, vmsUnavailableReason: String?) {
        snapshot = UnraidSnapshot(vms = vms, vmsUnavailableReason = vmsUnavailableReason, lastSuccessfulPoll = clock.instant())
        consecutiveFailures = 0
        lastFailure = null
    }

    private fun retainMissingOnce(current: List<UnraidVm>): List<UnraidVm> {
        val currentIds = current.map { it.id }.toSet()
        val retained = snapshot?.vms.orEmpty().filter { it.id !in currentIds && it.id !in missingVmIds }
        missingVmIds = retained.map { it.id }.toSet()
        return (current + retained).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun recordFailure(result: Result.Failure) {
        val reason = UnraidConverter.failureReason(result, config)
        logger.debug("Unraid poll failed: {}", reason)
        consecutiveFailures = when {
            UnraidConverter.isPermanent(result) -> maxOf(consecutiveFailures + 1, FAILURES_BEFORE_UNAVAILABLE)
            else -> consecutiveFailures + 1
        }
        lastFailure = reason
    }

    private fun configurationError(): String? = when {
        config.apiKey.isBlank() -> "unraid.apiKey must be set in configuration.yml"
        config.scheme !in SCHEMES -> "unraid.scheme must be http or https, not ${config.scheme}"
        config.caCertificatePath != null && !File(config.caCertificatePath).isFile ->
            "unraid.caCertificatePath ${config.caCertificatePath} is not a file"

        else -> null
    }
}
