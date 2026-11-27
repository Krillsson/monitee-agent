package com.krillsson.sysapi.core.metrics.defaultimpl

import com.krillsson.sysapi.Threads
import com.krillsson.sysapi.core.domain.filesystem.FileSystem
import com.krillsson.sysapi.core.domain.filesystem.FileSystemLoad
import com.krillsson.sysapi.core.metrics.FileSystemMetrics
import com.krillsson.sysapi.util.asHex
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import oshi.software.os.OSFileStore
import oshi.software.os.OperatingSystem
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.util.concurrent.TimeUnit

@Component
open class DefaultFileSystemMetrics(
    private val operatingSystem: OperatingSystem
) : FileSystemMetrics {

    private val fileSystemMetric = Sinks.many()
        .replay()
        .latest<List<FileSystemLoad>>()

    override fun fileSystems(): List<FileSystem> {
        return operatingSystem.fileSystem
            .fileStores.map {
                it.asFileSystem()
            }
            .distinctBy { it.id }
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES, scheduler = Threads.SAMPLING_SCHEDULER)
    fun runMeasurement() {
        fileSystemMetric.tryEmitNext(fileSystemLoads())
    }

    override fun fileSystemById(id: String): FileSystem? {
        return operatingSystem.fileSystem
            .fileStores
            .map { it.asFileSystem() }
            .firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    override fun fileSystemLoads(): List<FileSystemLoad> {
        return operatingSystem.fileSystem
            .fileStores.map {
                it.asFileSystemLoad()
            }
            .distinctBy { it.id }
    }

    override fun fileSystemLoadById(id: String): FileSystemLoad? {
        return operatingSystem.fileSystem
            .fileStores
            .map { it.asFileSystemLoad() }
            .firstOrNull { it.id.equals(id, ignoreCase = true) }
    }

    override fun fileSystemEvents(): Flux<List<FileSystemLoad>> {
        return fileSystemMetric.asFlux()
    }

    override fun fileSystemEventsById(id: String): Flux<FileSystemLoad> {
        return fileSystemMetric.asFlux()
            .mapNotNull { list: List<FileSystemLoad> ->
                list.firstOrNull { n -> n.id.equals(id, ignoreCase = true) }
            }
    }

    private fun OSFileStore.asFileSystemLoad(): FileSystemLoad {
        val id = if (uuid.isNullOrEmpty()) createFallbackUrl() else uuid
        return FileSystemLoad(
            name,
            id,
            freeSpace,
            usableSpace,
            totalSpace
        )
    }

    private fun OSFileStore.asFileSystem(): FileSystem {
        val id = if (uuid.isNullOrEmpty()) createFallbackUrl() else uuid
        return FileSystem(
            name,
            id,
            description,
            label,
            type,
            volume,
            mount,
            totalSpace
        )
    }

    private fun OSFileStore.createFallbackUrl(): String {
        return "fallback-id-${name.hashCode().asHex()}-${type.hashCode().asHex()}-${
            mount.hashCode().asHex()
        }-${volume.hashCode().asHex()}-${label.hashCode().asHex()}"
    }
}
