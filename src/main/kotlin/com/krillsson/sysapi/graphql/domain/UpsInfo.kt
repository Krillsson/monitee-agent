package com.krillsson.sysapi.graphql.domain

interface UpsInfo
object UpsInfoAvailable : UpsInfo

data class UpsInfoUnavailable(
        val reason: String,
        val isDisabled: Boolean
) : UpsInfo

