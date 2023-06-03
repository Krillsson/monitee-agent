package com.krillsson.sysapi.updatechecker

import com.krillsson.server.BuildConfig
import com.krillsson.sysapi.config.UpdateCheckConfiguration
import com.krillsson.sysapi.core.genericevents.GenericEvent
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.periodictasks.Task
import com.krillsson.sysapi.periodictasks.TaskInterval
import com.krillsson.sysapi.periodictasks.TaskManager
import com.krillsson.sysapi.util.logger
import java.time.OffsetDateTime
import java.util.*

class UpdateChecker(
    private val updateCheckConfiguration: UpdateCheckConfiguration,
    private val repository: GenericEventRepository,
    private val githubApiService: GithubApiService,
    taskManager: TaskManager
) : Task {
    private val logger by logger()

    override val key: Task.Key = Task.Key.CheckUpdate
    override val defaultInterval: TaskInterval = TaskInterval.VerySeldom

    init {
        taskManager.registerTask(this)
    }

    override fun run() {
        if (updateCheckConfiguration.enabled) {
            logger.info("Checking for new release")
            getLatestRelease()?.let { release ->
                val remoteVersion = Version(release.tag_name)
                val currentVersion = Version(BuildConfig.APP_VERSION)
                when {
                    remoteVersion > currentVersion -> createEventForRelease(release, currentVersion, remoteVersion)
                    remoteVersion <= currentVersion -> removeOutdatedEvents()
                    else -> logger.info("Already at latest version currentVersion: $currentVersion, remote: $remoteVersion")
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
            val newEvent = GenericEvent.UpdateAvailable(
                id = UUID.randomUUID(),
                dateTime = OffsetDateTime.now(),
                currentVersion = BuildConfig.APP_VERSION,
                newVersion = release.tag_name,
                changeLogMarkdown = release.body,
                downloadUrl = release.url,
                publishDate = release.published_at
            )
            repository.add(newEvent)
        } else {
            logger.info("Event already exists: new update available current: $currentVersion, remote: $remoteVersion")
        }
    }

    private fun getExistingEventForRelease(it: ApiResponse.Release) = repository.read()
        .firstOrNull { event -> (event as? GenericEvent.UpdateAvailable)?.newVersion == it.tag_name }

    private fun removeOutdatedEvents() {
        repository.read().filterIsInstance<GenericEvent.UpdateAvailable>().forEach { oldEvent ->
            logger.info("Removing event about ${oldEvent.newVersion} since its outdated")
            repository.removeById(oldEvent.id)
        }
    }

    private fun getLatestRelease(): ApiResponse.Release? {
        return try {
            githubApiService.getReleases(
                user = updateCheckConfiguration.user,
                repo = updateCheckConfiguration.repo
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