package com.krillsson.sysapi.config

data class InternetServicesCheckConfiguration(
    val enabled: Boolean = false,
    val services: List<InternetService> = emptyList()
) {
    data class InternetService(
        val address: String,
        val name: String,
        val port: Int
    ) {
        val id: String = address
    }
}