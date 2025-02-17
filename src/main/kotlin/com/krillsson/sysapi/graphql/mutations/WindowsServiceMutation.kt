package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.windows.services.WindowsServiceCommand

interface PerformWindowsServiceCommandOutput

data class PerformWindowsServiceCommandOutputSucceeded(
        val serviceName: String
) : PerformWindowsServiceCommandOutput

data class PerformWindowsServiceCommandOutputFailed(
        val reason: String
) : PerformWindowsServiceCommandOutput

data class PerformWindowsServiceCommandInput(
        val serviceName: String,
        val command: WindowsServiceCommand
)


