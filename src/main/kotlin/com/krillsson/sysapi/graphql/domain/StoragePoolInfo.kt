package com.krillsson.sysapi.graphql.domain

interface StoragePoolInfo
object StoragePoolInfoAvailable : StoragePoolInfo

data class StoragePoolInfoUnavailable(
    val reason: String,
    val isDisabled: Boolean
) : StoragePoolInfo
