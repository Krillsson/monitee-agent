package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.serverid.ServerIdService
import okhttp3.HttpUrl
import org.springframework.stereotype.Component
import java.util.*

@Component
class DeeplinkCreator(
    yamlConfigFile: YAMLConfigFile,
    private val serverIdService: ServerIdService,
) {

    private val updateCheckConfiguration = yamlConfigFile.updateCheck

    fun createDeeplink(notification: Notification): String {
        return when (notification) {
            is Notification.GenericEvent.MonitoredItemMissing -> monitoredItemMissing()
            is Notification.GenericEvent.UpdateAvailable -> githubRelease()
            is Notification.OngoingEvent -> ongoingEvent(notification.monitorId)
        }
    }

    fun ongoingEvent(monitorId: UUID): String {
        return createMoniteeDeeplink {
            addPathSegment("monitor")
            addPathSegment(monitorId.toString())
        }
    }

    fun monitoredItemMissing(): String {
        return createMoniteeDeeplink {
            addPathSegment("events")
        }
    }

    fun githubRelease(): String {
        return "https://github.com/${updateCheckConfiguration.user}/${updateCheckConfiguration.repo}/releases/latest"
    }

    private fun createMoniteeDeeplink(builder: HttpUrl.Builder.() -> Unit = {}): String {
        HttpUrl.Builder().apply {
            scheme("https")
            host("monitee.app")
            addPathSegment("server")
            addPathSegment(serverIdService.serverId.toString())
            builder(this)
        }.build().let { url ->
            return url.toString()
        }
    }

}