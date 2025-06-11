package com.krillsson.sysapi.graphql.domain

import java.util.*

data class Meta(
    val version: String,
    val buildDate: String,
    val processId: Int,
    val serverId: UUID,
    val endpoints: List<String>
)