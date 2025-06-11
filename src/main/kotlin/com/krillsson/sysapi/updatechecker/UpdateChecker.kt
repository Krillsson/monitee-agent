package com.krillsson.sysapi.updatechecker

import com.krillsson.sysapi.BuildConfig
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.genericevents.UpdateAvailable
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class UpdateChecker(
    private val config: YAMLConfigFile,
    private val repository: GenericEventRepository,
    private val githubApiService: GithubApiService,
    private val notificationManager: NotificationManager
) {
    private val logger by logger()

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (config.updateCheck.enabled) {
            logger.debug("Checking for new release")
            getLatestRelease()?.let { release ->
                val remoteVersion = Version(release.tag_name)
                val currentVersion = Version(BuildConfig.APP_VERSION)
                when {
                    remoteVersion > currentVersion -> createEventForRelease(release, currentVersion, remoteVersion)
                    remoteVersion <= currentVersion -> removeOutdatedEvents()
                    else -> logger.debug("Already at latest version currentVersion: $currentVersion, remote: $remoteVersion")
                }
            }
        }
    }

    private fun createEventForRelease(
        release: ApiResponse.Release,
        currentVersion: Version,
        remoteVersion: Version
    ) {
        val existingEvent = getExistingEventForRelease(release)
        if (existingEvent == null) {
            logger.info("Creating event: new update available current: $currentVersion, remote: $remoteVersion")
            val newEvent = UpdateAvailable(
                id = UUID.randomUUID(),
                timestamp = Instant.now(),
                currentVersion = BuildConfig.APP_VERSION,
                newVersion = release.tag_name,
                changeLogMarkdown = release.body,
                downloadUrl = release.url,
                publishDate = release.published_at
            )
            repository.add(newEvent)
            notificationManager.notify(newEvent.asNotification())
        } else {
            logger.debug("Event already exists: new update available current: $currentVersion, remote: $remoteVersion")
        }
    }

    private fun getExistingEventForRelease(it: ApiResponse.Release) = repository.read()
        .firstOrNull { event -> (event as? UpdateAvailable)?.newVersion == it.tag_name }

    private fun removeOutdatedEvents() {
        repository.read().filterIsInstance<UpdateAvailable>().forEach { oldEvent ->
            logger.info("Removing event about ${oldEvent.newVersion} since its outdated")
            repository.removeById(oldEvent.id)
        }
    }

    private fun getLatestRelease(): ApiResponse.Release? {
        return try {
            githubApiService.getReleases(
                user = config.updateCheck.user,
                repo = config.updateCheck.repo
            )
                .execute()
                .body()
                ?.firstOrNull()
        } catch (exception: Throwable) {
            logger.error("Error while querying Github ${exception.message}")
            null
        }
    }
}

private fun UpdateAvailable.asNotification(): Notification {
    return Notification.GenericEvent.UpdateAvailable(
        id = id,
        timestamp = timestamp,
        currentVersion = currentVersion,
        newVersion = newVersion,
        downloadUrl = downloadUrl,
        publishDate = publishDate
    )
}
