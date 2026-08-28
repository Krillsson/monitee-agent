package com.krillsson.sysapi.core.domain.docker

data class ContainerUpdateEligibility(
    val updatable: Boolean,
    val reason: String?
)
