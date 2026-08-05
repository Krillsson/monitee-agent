package com.krillsson.sysapi.graphql

import com.krillsson.sysapi.core.genericevents.ContainerImageUpdateAvailable
import com.krillsson.sysapi.core.genericevents.GenericEvent
import com.krillsson.sysapi.core.genericevents.MonitoredItemMissing
import com.krillsson.sysapi.core.genericevents.PackageUpdatesAvailable
import com.krillsson.sysapi.util.toOffsetDateTime
import org.springframework.graphql.data.method.annotation.SchemaMapping
import org.springframework.stereotype.Controller

//@Controller
//@SchemaMapping(typeName = "GenericEvent")
//class GenericEventResolver {
//    @SchemaMapping
//    fun title(event: GenericEvent): String {
//        return when(event){
//            is GenericEvent.MonitoredItemMissing -> "Monitored item is missing"
//            is GenericEvent.UpdateAvailable -> "sys-API update available"
//        }
//    }
//
//    @SchemaMapping
//    fun description(event: GenericEvent): String {
//        return when(event){
//            is GenericEvent.MonitoredItemMissing -> "${event.monitorType.name} monitor's item ${event.monitoredItemId} is no longer present in the system"
//            is GenericEvent.UpdateAvailable ->"New version ${event.newVersion} published at ${event.publishDate}. Server is running ${event.currentVersion}"
//        }
//    }
//
//    @SchemaMapping
//    fun dateTime(event: GenericEvent) = event.timestamp.toOffsetDateTime()
//}

@Controller
@SchemaMapping(typeName = "ContainerImageUpdateAvailable")
class ContainerImageUpdateAvailableGenericEventResolver {
    @SchemaMapping(typeName = "ContainerImageUpdateAvailable", field = "title")
    fun title(event: ContainerImageUpdateAvailable): String {
        return "New container image available"
    }

    @SchemaMapping(typeName = "ContainerImageUpdateAvailable", field = "description")
    fun description(event: ContainerImageUpdateAvailable): String {
        return "${event.containerName} is running an outdated ${event.imageRef}"
    }

    @SchemaMapping(typeName = "ContainerImageUpdateAvailable", field = "dateTime")
    fun dateTime(event: ContainerImageUpdateAvailable) = event.timestamp.toOffsetDateTime()
}

@Controller
@SchemaMapping(typeName = "PackageUpdatesAvailable")
class PackageUpdatesAvailableGenericEventResolver {
    @SchemaMapping(typeName = "PackageUpdatesAvailable", field = "title")
    fun title(event: PackageUpdatesAvailable): String {
        return "Package updates available"
    }

    @SchemaMapping(typeName = "PackageUpdatesAvailable", field = "description")
    fun description(event: PackageUpdatesAvailable): String {
        val packages = if (event.totalCount == 1) "1 package" else "${event.totalCount} packages"
        return if (event.securityCount != null && event.securityCount > 0) {
            "$packages can be updated with ${event.manager}, ${event.securityCount} of them security"
        } else {
            "$packages can be updated with ${event.manager}"
        }
    }

    @SchemaMapping(typeName = "PackageUpdatesAvailable", field = "dateTime")
    fun dateTime(event: PackageUpdatesAvailable) = event.timestamp.toOffsetDateTime()
}

@Controller
@SchemaMapping(typeName = "MonitoredItemMissing")
class MonitoredItemMissingGenericEventResolver {
    @SchemaMapping(typeName = "MonitoredItemMissing", field = "title")
    fun title(event: MonitoredItemMissing): String {
        return "Monitored item is missing"
    }

    @SchemaMapping(typeName = "MonitoredItemMissing", field = "description")
    fun description(event: MonitoredItemMissing): String {
        return "${event.monitorType.name} monitor's item ${event.monitoredItemId} is no longer present in the system"
    }

    @SchemaMapping(typeName = "MonitoredItemMissing", field = "dateTime")
    fun dateTime(event: MonitoredItemMissing) = event.timestamp.toOffsetDateTime()
}