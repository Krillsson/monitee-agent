package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.ComposeLabels
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.ContainerGroup

fun List<Container>.groupByComposeProject(): List<ContainerGroup> {
    val (grouped, ungrouped) = partition { !it.labels[ComposeLabels.PROJECT].isNullOrBlank() }
    val groups = grouped
        .groupBy { it.labels.getValue(ComposeLabels.PROJECT) }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (project, containers) -> ContainerGroup(project, containers) }
    return if (ungrouped.isEmpty()) groups else groups + ContainerGroup(null, ungrouped)
}
