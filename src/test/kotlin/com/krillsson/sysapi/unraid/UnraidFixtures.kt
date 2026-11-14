package com.krillsson.sysapi.unraid

import java.time.Instant

const val WINDOWS_UUID = "5e1f3c2a-8b4d-4f6e-9a7b-1c2d3e4f5a6b"
const val HOME_ASSISTANT_UUID = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"

fun unraidVm(
    id: String = WINDOWS_UUID,
    name: String = "Windows 11",
    state: UnraidVmState = UnraidVmState.RUNNING
): UnraidVm = UnraidVm(id = id, name = name, state = state)

fun unraidSnapshot(
    vararg vms: UnraidVm,
    vmsUnavailableReason: String? = null,
    lastSuccessfulPoll: Instant = Instant.parse("2026-11-14T10:00:00Z")
): UnraidSnapshot = UnraidSnapshot(vms.toList(), vmsUnavailableReason, lastSuccessfulPoll)
