package com.krillsson.sysapi.workload.icon

data class WorkloadIcon(
    val name: String,
    val isFallback: Boolean,
    val svg: String?,
    val png: String?,
    val webp: String?
)
