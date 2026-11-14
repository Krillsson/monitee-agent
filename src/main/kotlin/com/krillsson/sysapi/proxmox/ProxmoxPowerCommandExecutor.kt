package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import com.krillsson.sysapi.util.logger
import com.krillsson.sysapi.workload.PowerCommand
import com.krillsson.sysapi.workload.PowerCommandExecutor
import com.krillsson.sysapi.workload.PowerCommandExecutor.Admission
import com.krillsson.sysapi.workload.PowerCommandProgress
import com.krillsson.sysapi.workload.WorkloadPlatform
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class ProxmoxPowerCommandExecutor(
    configuration: YAMLConfigFile,
    private val client: ProxmoxClient,
    private val proxmoxService: ProxmoxService,
    private val taskScheduler: TaskScheduler,
    private val clock: Clock
) : PowerCommandExecutor {
    companion object {
        const val FAILED_CHECKS_BEFORE_GIVING_UP = 3
        const val LOG_TAIL_LINES = 20
        val CHECK_INTERVAL: Duration = Duration.ofSeconds(2)
        private val SHUTDOWN_GRACE: Duration = Duration.ofSeconds(30)
        private val SHUTDOWN_TIMEOUT_FAILURES = listOf("got timeout", "container did not stop")
    }

    private class Task(
        val guest: ProxmoxGuest,
        val command: PowerCommand,
        val upid: String,
        val deadline: Instant,
        val progress: PowerCommandProgress
    ) {
        var failedChecks = 0
    }

    private val logger by logger()

    private val config = configuration.proxmox

    private val timeout: Duration = maxOf(
        Duration.ofSeconds(config.commandTimeoutSeconds),
        Duration.ofSeconds(config.shutdownTimeoutSeconds).plus(SHUTDOWN_GRACE)
    )

    override val platform: WorkloadPlatform = WorkloadPlatform.PROXMOX

    override fun admit(id: String, command: PowerCommand): Admission {
        val status = proxmoxService.status()
        val guest = proxmoxService.guest(id)
        return when {
            status is ProxmoxService.Status.Unavailable -> Admission.Refused(status.reason)
            status is ProxmoxService.Status.Disabled -> Admission.Refused("Proxmox is not enabled in configuration.yml")
            guest == null -> Admission.Refused("There is no guest with id $id")
            else -> proxmoxService.commandRefusal(guest, command)?.let { Admission.Refused(it) } ?: Admission.Admitted(guest.name)
        }
    }

    override fun run(id: String, command: PowerCommand, progress: PowerCommandProgress) {
        when (val guest = proxmoxService.guest(id)) {
            null -> progress.failed("There is no guest with id $id")
            else -> post(guest, command, progress)
        }
    }

    private fun post(guest: ProxmoxGuest, command: PowerCommand, progress: PowerCommandProgress) {
        val path = "${guest.apiPath()}/status/${ProxmoxGuestCommands.action(command)}"
        val form = ProxmoxGuestCommands.formFor(command, config.shutdownTimeoutSeconds)
        when (val result = client.post(path, form, String::class.java)) {
            is Result.Ok -> {
                progress.running(result.data)
                scheduleCheck(Task(guest, command, result.data, clock.instant().plus(timeout), progress))
            }

            is Result.Failure -> progress.failed(describe(result))
        }
    }

    private fun scheduleCheck(task: Task) {
        taskScheduler.schedule({ check(task) }, clock.instant().plus(CHECK_INTERVAL))
    }

    private fun check(task: Task) {
        val result = try {
            client.get("/nodes/${task.guest.node}/tasks/${task.upid}/status", ProxmoxApi.TaskStatus::class.java)
        } catch (exception: Exception) {
            Result.TransportFailure(exception)
        }
        when (result) {
            is Result.Ok -> task.failedChecks = 0
            is Result.Failure -> task.failedChecks++
        }
        when {
            result is Result.Ok && result.data.status == "stopped" -> finish(task, result.data.exitStatus)
            result is Result.Failure && task.failedChecks >= FAILED_CHECKS_BEFORE_GIVING_UP ->
                fail(task, "Lost track of the task: ${describe(result)}")

            clock.instant() >= task.deadline ->
                fail(task, "Proxmox did not finish the task within ${timeout.seconds} seconds, it may still be running there")

            else -> scheduleCheck(task)
        }
    }

    private fun finish(task: Task, exitStatus: String?) {
        when {
            exitStatus == "OK" || exitStatus?.startsWith("WARNINGS") == true -> {
                refresh(task.guest)
                task.progress.succeeded(task.upid, exitStatus)
            }

            else -> fail(task, exitStatus ?: "Proxmox reported no exit status for the task", taskLog(task))
        }
    }

    private fun fail(task: Task, reason: String, log: List<String> = emptyList()) {
        val canForceStop = task.command in setOf(PowerCommand.SHUTDOWN, PowerCommand.REBOOT) &&
            SHUTDOWN_TIMEOUT_FAILURES.any { reason.contains(it) }
        refresh(task.guest)
        task.progress.failed(reason, task.upid, log, canForceStop)
    }

    private fun taskLog(task: Task): List<String> {
        val result = client.getList("/nodes/${task.guest.node}/tasks/${task.upid}/log", ProxmoxApi.TaskLogLine::class.java)
        return (result as? Result.Ok)?.data.orEmpty().mapNotNull { it.t }.takeLast(LOG_TAIL_LINES)
    }

    private fun refresh(guest: ProxmoxGuest) {
        try {
            proxmoxService.guestChanged(guest)
        } catch (exception: Exception) {
            logger.warn("Failed to refresh Proxmox after a command on guest {}", guest.id, exception)
        }
    }

    private fun describe(result: Result.Failure): String = ProxmoxConverter.failureReason(result, config.host, config.port)
}
