package com.krillsson.sysapi.core.genericevents

import com.krillsson.sysapi.util.toOffsetDateTime
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GenericEventRepository(private val store: GenericEventStore) {

    fun read(): List<GenericEvent> {
        return store.read().orEmpty().map { it.asDomain() }
    }

    fun write(value: List<GenericEvent>) {
        store.write(value.mapNotNull { it.asStored() })
    }

    fun add(event: GenericEvent) {
        update {
            val list = it.toMutableList()
            list.add(event)
            list
        }
    }

    fun update(event: GenericEvent) {
        update {
            val list = it.toMutableList()
            list.removeIf { toBeRemoved -> toBeRemoved.id == event.id }
            list.add(event)
            list
        }
    }

    fun removeById(id: UUID): Boolean {
        var result = false
        update {
            val list = it.toMutableList()
            result = list.removeIf { event -> event.id == id }
            list
        }
        return result
    }

    private fun update(action: (List<GenericEvent>) -> List<GenericEvent>) {
        val previousValue = read()
        val newValue = action(previousValue)
        write(newValue)
    }

    private fun GenericEvent.asStored(): GenericEventStore.StoredGenericEvent? {
        return when (this) {
            is MonitoredItemMissing -> GenericEventStore.StoredGenericEvent.MonitoredItemMissing(
                id = id,
                dateTime = timestamp.toOffsetDateTime(),
                monitorType = monitorType,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId
            )

            is UpdateAvailable -> GenericEventStore.StoredGenericEvent.UpdateAvailable(
                id = id,
                dateTime = timestamp.toOffsetDateTime(),
                currentVersion = currentVersion,
                newVersion = newVersion,
                changeLogMarkdown = changeLogMarkdown,
                downloadUrl = downloadUrl,
                publishDate = publishDate
            )

            is ContainerImageUpdateAvailable -> GenericEventStore.StoredGenericEvent.ContainerImageUpdateAvailable(
                id = id,
                dateTime = timestamp.toOffsetDateTime(),
                containerId = containerId,
                containerName = containerName,
                imageRef = imageRef,
                remoteDigest = remoteDigest
            )

            is PackageUpdatesAvailable -> GenericEventStore.StoredGenericEvent.PackageUpdatesAvailable(
                id = id,
                dateTime = timestamp.toOffsetDateTime(),
                manager = manager,
                totalCount = totalCount,
                securityCount = securityCount
            )

            else -> null
        }
    }

    private fun GenericEventStore.StoredGenericEvent.asDomain(): GenericEvent {
        return when (this) {
            is GenericEventStore.StoredGenericEvent.MonitoredItemMissing -> MonitoredItemMissing(
                id = id,
                timestamp = dateTime.toInstant(),
                monitorType = monitorType,
                monitorId = monitorId,
                monitoredItemId = monitoredItemId
            )

            is GenericEventStore.StoredGenericEvent.UpdateAvailable -> UpdateAvailable(
                id = id,
                timestamp = dateTime.toInstant(),
                currentVersion = currentVersion,
                newVersion = newVersion,
                changeLogMarkdown = changeLogMarkdown,
                downloadUrl = downloadUrl,
                publishDate = publishDate
            )

            is GenericEventStore.StoredGenericEvent.ContainerImageUpdateAvailable -> ContainerImageUpdateAvailable(
                id = id,
                timestamp = dateTime.toInstant(),
                containerId = containerId,
                containerName = containerName,
                imageRef = imageRef,
                remoteDigest = remoteDigest
            )

            is GenericEventStore.StoredGenericEvent.PackageUpdatesAvailable -> PackageUpdatesAvailable(
                id = id,
                timestamp = dateTime.toInstant(),
                manager = manager,
                totalCount = totalCount,
                securityCount = securityCount
            )
        }
    }
}