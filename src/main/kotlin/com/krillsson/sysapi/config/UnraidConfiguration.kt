package com.krillsson.sysapi.config

data class UnraidConfiguration(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 80,
    val scheme: String = "http",
    val apiKey: String = "",
    val verifyTls: Boolean = true,
    val caCertificatePath: String? = null,
    val pollIntervalSeconds: Long = 15,
    val commandTimeoutSeconds: Long = 120
)
