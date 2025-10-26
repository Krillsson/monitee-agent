package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry
import com.krillsson.sysapi.docker.ContainerService
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class ContainerResolver(val containerService: ContainerService) {
    @SchemaMapping(typeName = "DockerContainerMetricsHistoryEntry", field = "metrics")
    fun metrics(container: ContainerMetricsHistoryEntry) = containerService.statsForContainer(container.containerId)
}