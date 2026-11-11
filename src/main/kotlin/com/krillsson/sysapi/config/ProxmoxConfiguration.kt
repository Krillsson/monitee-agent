package com.krillsson.sysapi.config

data class ProxmoxConfiguration(
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 8006,
    val tokenId: String = "",
    val secret: String = "",
    val verifyTls: Boolean = true,
    val caCertificatePath: String? = null,
    val pollIntervalSeconds: Long = 15,
    val includeTemplates: Boolean = false
)
