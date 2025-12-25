package com.krillsson.sysapi.graphql.domain

data class InternetService(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val available: Boolean,
    val failureMessage: String?,
    val latencyMs: Long,
)

