package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.domain.filesystem.FileSystemSpaceForecast
import com.krillsson.sysapi.core.forecast.FileSystemSpaceForecastDAO
import com.krillsson.sysapi.core.forecast.asDomain
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.stereotype.Controller

@Controller
class FileSystemResolver(
    val metrics: Metrics,
    val forecastDAO: FileSystemSpaceForecastDAO
) {
    @BatchMapping(typeName = "FileSystem", field = "metrics")
    fun metrics(fileSystems: List<FileSystem>): Map<FileSystem, FileSystemLoad?> {
        val loadsById = metrics.fileSystemMetrics().fileSystemLoads().associateBy { it.id }
        return fileSystems.associateWith { loadsById[it.id] }
    }

    @BatchMapping(typeName = "FileSystem", field = "spaceForecast")
    fun spaceForecast(fileSystems: List<FileSystem>): Map<FileSystem, FileSystemSpaceForecast?> {
        val forecastsById = forecastDAO.findAllById(fileSystems.map { it.id }).associateBy { it.filesystemId }
        return fileSystems.associateWith { fileSystem -> forecastsById[fileSystem.id]?.asDomain() }
    }
}
