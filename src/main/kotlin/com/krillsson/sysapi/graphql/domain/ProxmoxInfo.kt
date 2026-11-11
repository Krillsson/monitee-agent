package com.krillsson.sysapi.graphql.domain

interface ProxmoxInfo
object ProxmoxInfoAvailable : ProxmoxInfo

data class ProxmoxInfoUnavailable(
    val reason: String,
    val isDisabled: Boolean
) : ProxmoxInfo
