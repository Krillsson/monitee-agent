package com.krillsson.sysapi.proxmox

object ProxmoxGuestCommands {

    private val PAUSED_QMP_STATES = setOf("paused", "suspended")
    private val BLOCKING_HA_STATES = setOf("error", "fence", "recovery", "freeze", "migrate", "relocate")
    private val RUNNING_COMMANDS = setOf(
        ProxmoxGuestCommand.SHUTDOWN,
        ProxmoxGuestCommand.FORCE_STOP,
        ProxmoxGuestCommand.REBOOT,
        ProxmoxGuestCommand.SUSPEND
    )
    private val PAUSED_COMMANDS = setOf(ProxmoxGuestCommand.FORCE_STOP, ProxmoxGuestCommand.RESUME)
    private val QEMU_ONLY_COMMANDS = setOf(ProxmoxGuestCommand.SUSPEND, ProxmoxGuestCommand.RESUME)

    fun supported(guest: ProxmoxGuest, quorate: Boolean, qmpStatus: String?): List<ProxmoxGuestCommand> =
        ProxmoxGuestCommand.entries.filter { refusal(guest, it, quorate, qmpStatus) == null }

    fun refusal(guest: ProxmoxGuest, command: ProxmoxGuestCommand, quorate: Boolean, qmpStatus: String?): String? = when {
        guest.template -> "${guest.name} is a template"
        guest.lock != null -> "${guest.name} is locked by Proxmox (${guest.lock})"
        !quorate -> "The cluster has no quorum"
        guest.haState in BLOCKING_HA_STATES -> "${guest.name} is in HA state ${guest.haState}"
        guest.kind == ProxmoxGuestKind.LXC && command in QEMU_ONLY_COMMANDS -> "Containers cannot be suspended or resumed"

        else -> stateRefusal(guest, command, qmpStatus in PAUSED_QMP_STATES)
    }

    fun formFor(command: ProxmoxGuestCommand, shutdownTimeoutSeconds: Long): Map<String, String> = when (command) {
        ProxmoxGuestCommand.SHUTDOWN -> mapOf("timeout" to shutdownTimeoutSeconds.toString(), "forceStop" to "0")
        ProxmoxGuestCommand.REBOOT -> mapOf("timeout" to shutdownTimeoutSeconds.toString())
        else -> emptyMap()
    }

    private fun stateRefusal(guest: ProxmoxGuest, command: ProxmoxGuestCommand, paused: Boolean): String? = when (guest.status) {
        ProxmoxGuestStatus.UNKNOWN -> "The state of ${guest.name} is unknown, its node may be offline"
        ProxmoxGuestStatus.STOPPED -> if (command == ProxmoxGuestCommand.START) null else "${guest.name} is not running"
        ProxmoxGuestStatus.RUNNING -> when {
            command == ProxmoxGuestCommand.START -> "${guest.name} is already running"
            paused && command !in PAUSED_COMMANDS -> "${guest.name} is suspended"
            !paused && command !in RUNNING_COMMANDS -> "${guest.name} is not suspended"
            else -> null
        }
    }
}
