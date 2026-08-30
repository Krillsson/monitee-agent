package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.metrics.Metrics
import org.springframework.graphql.data.method.annotation.BatchMapping
import org.springframework.stereotype.Controller

@Controller
class FileSystemResolver(val metrics: Metrics) {
    @BatchMapping(typeName = "FileSystem", field = "metrics")
    fun metrics(fileSystems: List<FileSystem>): Map<FileSystem, FileSystemLoad?> {
        val loadsById = metrics.fileSystemMetrics().fileSystemLoads().associateBy { it.id }
        return fileSystems.associateWith { loadsById[it.id] }
    }
}