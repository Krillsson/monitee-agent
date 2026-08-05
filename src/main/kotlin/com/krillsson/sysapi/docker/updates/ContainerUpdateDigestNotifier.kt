package com.krillsson.sysapi.docker.updates

import com.krillsson.sysapi.config.ContainerUpdateNotifyStyle
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.genericevents.ContainerImageUpdateAvailable
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.notifications.Notification
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@Service
class ContainerUpdateDigestNotifier(
    private val genericEventRepository: GenericEventRepository,
    private val notificationManager: NotificationManager,
    yamlConfigFile: YAMLConfigFile
) {
    private val logger by logger()

    private val config = yamlConfigFile.docker.updateCheck
    private val digestAt = LocalTime.of(config.digestAtHour.coerceIn(0, 23), 0)

    @Volatile
    private var nextDigest = digestAfter(LocalDateTime.now())

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (!enabled()) {
            return
        }

        val now = LocalDateTime.now()
        if (now.isBefore(nextDigest)) {
            return
        }
        nextDigest = digestAfter(now)

        val containerNames = genericEventRepository.read()
            .filterIsInstance<ContainerImageUpdateAvailable>()
            .map { it.containerName }
            .distinct()
        if (containerNames.isEmpty()) {
            logger.debug("No outdated containers to include in the daily digest")
            return
        }

        logger.info("Sending daily digest for {} outdated containers", containerNames.size)
        notificationManager.notify(
            Notification.GenericEvent.ContainerImageUpdateDigest(
                timestamp = Instant.now(),
                containerNames = containerNames
            )
        )
    }

    private fun enabled() = config.enabled &&
            config.notify &&
            config.notifyStyle == ContainerUpdateNotifyStyle.DAILY_DIGEST

    private fun digestAfter(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(digestAt)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
