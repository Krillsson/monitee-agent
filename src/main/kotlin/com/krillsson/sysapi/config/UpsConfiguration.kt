package com.krillsson.sysapi.config

data class UpsConfiguration(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 3493
)