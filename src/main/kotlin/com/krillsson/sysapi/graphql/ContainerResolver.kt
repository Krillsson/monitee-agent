package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerImageUpdate
import com.krillsson.sysapi.core.domain.docker.ContainerMetricsHistoryEntry
import com.krillsson.sysapi.core.domain.docker.ContainerUpdateEligibility
import com.krillsson.sysapi.docker.ContainerRecreateService
import com.krillsson.sysapi.docker.ContainerService
import com.krillsson.sysapi.docker.updates.ContainerUpdateChecker
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

@Controller
class ContainerResolver(
    val containerService: ContainerService,
    val containerUpdateChecker: ContainerUpdateChecker,
    val containerRecreateService: ContainerRecreateService
) {
    @SchemaMapping(typeName = "DockerContainerMetricsHistoryEntry", field = "metrics")
    fun metrics(container: ContainerMetricsHistoryEntry) = containerService.statsForContainer(container.containerId)

    @SchemaMapping(typeName = "DockerContainer", field = "imageUpdate")
    fun imageUpdate(container: Container): ContainerImageUpdate =
        containerUpdateChecker.updateForContainer(container.id)
            ?: ContainerImageUpdate.notCheckedYet(container.id, container.image)

    @SchemaMapping(typeName = "DockerContainer", field = "updateEligibility")
    fun updateEligibility(container: Container): ContainerUpdateEligibility =
        containerRecreateService.updateEligibility(container)
}