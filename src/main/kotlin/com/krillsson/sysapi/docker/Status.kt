package com.krillsson.sysapi.docker

sealed class Status {
    object Available : Status()
    object Disabled : Status()
    data class Unavailable(val error: Throwable) : Status()
}

fun Status.unavailableReason(): String? = when (this) {
    Status.Available -> null
    Status.Disabled -> "The docker support is currently disabled. You can change this in configuration.yml"
    is Status.Unavailable -> "${error.message ?: "Unknown reason"} Type: ${requireNotNull(error::class.simpleName)}"
}
