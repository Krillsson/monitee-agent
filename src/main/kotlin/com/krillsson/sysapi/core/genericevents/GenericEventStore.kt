package com.krillsson.sysapi.core.genericevents

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.persistence.JsonFile
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.*

@Component
class GenericEventStore(objectMapper: ObjectMapper) :
    JsonFile<List<GenericEventStore.StoredGenericEvent>>(fileName, MONITORS_TYPE_REFERENCE, objectMapper) {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY)
    @JsonSubTypes(
        JsonSubTypes.Type(value = StoredGenericEvent.UpdateAvailable::class, name = "UpdateAvailable"),
        JsonSubTypes.Type(value = StoredGenericEvent.MonitoredItemMissing::class, name = "MonitoredItemMissing"),
    )
    sealed interface StoredGenericEvent {
        val type: String
            @JsonProperty("@type")
            get

        data class UpdateAvailable(
            val id: UUID,
            val dateTime: OffsetDateTime,
            val currentVersion: String,
            val newVersion: String,
            val changeLogMarkdown: String,
            val downloadUrl: String,
            val publishDate: String,
        ) : StoredGenericEvent {
            override val type: String = "UpdateAvailable"
        }

        data class MonitoredItemMissing(
            val id: UUID,
            val dateTime: OffsetDateTime,
            val monitorType: Monitor.Type,
            val monitorId: UUID,
            val monitoredItemId: String?
        ) : StoredGenericEvent {
            override val type: String = "MonitoredItemMissing"
        }
    }

    companion object {
        val MONITORS_TYPE_REFERENCE: TypeReference<List<StoredGenericEvent>> =
            object : TypeReference<List<StoredGenericEvent>>() {}

        const val fileName = "generic_events.json"
    }
}