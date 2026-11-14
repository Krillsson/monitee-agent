package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommand
import com.krillsson.sysapi.proxmox.ProxmoxGuestCommands
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetwork
import com.krillsson.sysapi.proxmox.ProxmoxGuestNetworkResult
import com.krillsson.sysapi.proxmox.ProxmoxService

fun ProxmoxService.listedCommands(guest: ProxmoxGuest): List<ProxmoxGuestCommand> =
    ProxmoxGuestCommands.supported(guest, quorate = snapshot()?.cluster?.quorate ?: false, qmpStatus = null)

fun ProxmoxService.availableNetwork(guest: ProxmoxGuest): ProxmoxGuestNetwork? =
    (guestNetwork(guest) as? ProxmoxGuestNetworkResult.Available)?.network

fun ProxmoxService.networkUnavailableReason(guest: ProxmoxGuest): String? =
    (guestNetwork(guest) as? ProxmoxGuestNetworkResult.Unavailable)?.reason
