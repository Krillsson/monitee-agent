package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.config.PackageUpdateNotifyStyle
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.system.Platform
import com.krillsson.sysapi.core.domain.system.SystemUpdates
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.genericevents.PackageUpdatesAvailable
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class PackageUpdatesChecker(
    private val genericEventRepository: GenericEventRepository,
    private val notificationManager: NotificationManager,
    platform: Platform,
    yamlConfigFile: YAMLConfigFile
) {
    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val reason: String) : Status()
    }

    companion object {
        private val RETRY_INTERVAL_AFTER_ERROR = Duration.ofMinutes(15)
        private val CONTAINER_MARKERS = listOf("docker", "containerd", "lxc", "kubepods")
    }

    private val logger by logger()

    private val config = yamlConfigFile.packageUpdates
    private val interval: Duration = Duration.ofHours(config.intervalHours)

    private val probes: List<PackageManagerProbe> = when (platform) {
        Platform.WINDOWS -> listOf(WindowsUpdateProbe())
        Platform.LINUX -> listOf(AptProbe(), DnfProbe("dnf"), DnfProbe("yum"), ZypperProbe(), PacmanProbe(), ApkProbe())
        else -> emptyList()
    }

    private val probe: PackageManagerProbe? by lazy { probes.firstOrNull { it.isSupported() } }

    @Volatile
    private var latest: SystemUpdates? = null

    @Volatile
    private var nextCheck: Instant = Instant.now()

    val status: Status by lazy {
        when {
            !config.enabled -> Status.Disabled
            runningInContainer() -> Status.Unavailable("The agent is running in a container, where the packages it can see belong to the image and not to the host")
            probe != null -> Status.Available
            else -> Status.Unavailable("No supported package manager was found")
        }
    }

    fun latest(): SystemUpdates? {
        if (status != Status.Available) {
            return null
        }
        if (latest == null) {
            sweep()
        }
        return latest
    }

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (status != Status.Available) {
            return
        }
        sweep()
    }

    @Synchronized
    private fun sweep() {
        if (Instant.now().isBefore(nextCheck)) {
            return
        }
        val probe = probe ?: return
        when (val result = probe.check()) {
            is ProbeResult.Success -> {
                nextCheck = Instant.now().plus(interval)
                val updates = SystemUpdates(
                    manager = probe.manager,
                    totalCount = result.packages.size,
                    securityCount = result.securityCount,
                    packages = result.packages,
                    checkedAt = Instant.now()
                )
                latest = updates
                logger.debug(
                    "{} reports {} pending updates, {} of them security",
                    updates.manager,
                    updates.totalCount,
                    updates.securityCount
                )
                reflectInEvents(updates)
            }

            is ProbeResult.Failed -> {
                nextCheck = Instant.now().plus(RETRY_INTERVAL_AFTER_ERROR)
                logger.warn("Could not read pending updates from {}: {}", probe.manager, result.reason)
            }
        }
    }

    private fun reflectInEvents(updates: SystemUpdates) {
        val existing = existingEvents()
        if (updates.totalCount == 0) {
            existing.forEach { genericEventRepository.removeById(it.id) }
            return
        }

        if (existing.any { it.totalCount == updates.totalCount && it.securityCount == updates.securityCount }) {
            return
        }

        existing.forEach { genericEventRepository.removeById(it.id) }
        logger.info("Creating event: {} packages can be updated with {}", updates.totalCount, updates.manager)
        val event = PackageUpdatesAvailable(
            id = UUID.randomUUID(),
            timestamp = Instant.now(),
            manager = updates.manager,
            totalCount = updates.totalCount,
            securityCount = updates.securityCount
        )
        genericEventRepository.add(event)
        if (config.notify && config.notifyStyle == PackageUpdateNotifyStyle.IMMEDIATELY) {
            notificationManager.notify(event.asNotification())
        }
    }

    private fun existingEvents() = genericEventRepository.read().filterIsInstance<PackageUpdatesAvailable>()

    private fun runningInContainer(): Boolean {
        if (File("/.dockerenv").exists() || File("/run/.containerenv").exists()) {
            return true
        }
        val cgroup = File("/proc/1/cgroup")
        if (!cgroup.exists()) {
            return false
        }
        val contents = runCatching { cgroup.readText() }.getOrDefault("")
        return CONTAINER_MARKERS.any { contents.contains(it) }
    }
}

fun PackageUpdatesAvailable.asNotification(): Notification {
    return Notification.GenericEvent.PackageUpdatesAvailable(
        id = id,
        timestamp = timestamp,
        manager = manager,
        totalCount = totalCount,
        securityCount = securityCount
    )
}
