package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.workload.PowerCommand

object ProxmoxGuestCommands {

    private val PAUSED_QMP_STATES = setOf("paused", "suspended")
    private val BLOCKING_HA_STATES = setOf("error", "fence", "recovery", "freeze", "migrate", "relocate")
    private val RUNNING_COMMANDS = setOf(
        PowerCommand.SHUTDOWN,
        PowerCommand.FORCE_STOP,
        PowerCommand.REBOOT,
        PowerCommand.PAUSE
    )
    private val PAUSED_COMMANDS = setOf(PowerCommand.FORCE_STOP, PowerCommand.RESUME)
    private val QEMU_ONLY_COMMANDS = setOf(PowerCommand.PAUSE, PowerCommand.RESUME)

    fun supported(guest: ProxmoxGuest, quorate: Boolean, qmpStatus: String?): List<PowerCommand> =
        PowerCommand.entries.filter { refusal(guest, it, quorate, qmpStatus) == null }

    fun refusal(guest: ProxmoxGuest, command: PowerCommand, quorate: Boolean, qmpStatus: String?): String? = when {
        guest.template -> "${guest.name} is a template"
        guest.lock != null -> "${guest.name} is locked by Proxmox (${guest.lock})"
        !quorate -> "The cluster has no quorum"
        guest.haState in BLOCKING_HA_STATES -> "${guest.name} is in HA state ${guest.haState}"
        guest.kind == ProxmoxGuestKind.LXC && command in QEMU_ONLY_COMMANDS -> "Containers cannot be paused or resumed"

        else -> stateRefusal(guest, command, qmpStatus in PAUSED_QMP_STATES)
    }

    fun action(command: PowerCommand): String = when (command) {
        PowerCommand.START -> "start"
        PowerCommand.SHUTDOWN -> "shutdown"
        PowerCommand.FORCE_STOP -> "stop"
        PowerCommand.REBOOT -> "reboot"
        PowerCommand.PAUSE -> "suspend"
        PowerCommand.RESUME -> "resume"
    }

    fun formFor(command: PowerCommand, shutdownTimeoutSeconds: Long): Map<String, String> = when (command) {
        PowerCommand.SHUTDOWN -> mapOf("timeout" to shutdownTimeoutSeconds.toString(), "forceStop" to "0")
        PowerCommand.REBOOT -> mapOf("timeout" to shutdownTimeoutSeconds.toString())
        else -> emptyMap()
    }

    private fun stateRefusal(guest: ProxmoxGuest, command: PowerCommand, paused: Boolean): String? = when (guest.status) {
        ProxmoxGuestStatus.UNKNOWN -> "The state of ${guest.name} is unknown, its node may be offline"
        ProxmoxGuestStatus.STOPPED -> if (command == PowerCommand.START) null else "${guest.name} is not running"
        ProxmoxGuestStatus.RUNNING -> when {
            command == PowerCommand.START -> "${guest.name} is already running"
            paused && command !in PAUSED_COMMANDS -> "${guest.name} is paused"
            !paused && command !in RUNNING_COMMANDS -> "${guest.name} is not paused"
            else -> null
        }
    }
}
