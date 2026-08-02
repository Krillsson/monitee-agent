package com.krillsson.sysapi.core.genericevents

import com.krillsson.sysapi.core.monitoring.Monitor
import java.time.Instant
import java.util.*

abstract class GenericEvent(
    val id: UUID,
    val timestamp: Instant
)


    class UpdateAvailable(
        id: UUID,
        timestamp: Instant,
        val currentVersion: String,
        val newVersion: String,
        val changeLogMarkdown: String,
        val downloadUrl: String,
        val publishDate: String,
    ) : GenericEvent(id, timestamp)

    class MonitoredItemMissing(
        id: UUID,
        timestamp: Instant,
        val monitorType: Monitor.Type,
        val monitorId: UUID,
        val monitoredItemId: String?
    ) : GenericEvent(id, timestamp)

    class ContainerImageUpdateAvailable(
        id: UUID,
        timestamp: Instant,
        val containerId: String,
        val containerName: String,
        val imageRef: String,
        val remoteDigest: String
    ) : GenericEvent(id, timestamp)
