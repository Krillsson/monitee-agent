package com.krillsson.sysapi.core.domain.docker

data class ContainerGroup(
    val composeProject: String?,
    val containers: List<Container>
)
