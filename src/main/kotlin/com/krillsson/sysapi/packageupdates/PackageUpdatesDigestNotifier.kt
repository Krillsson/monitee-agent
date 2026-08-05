package com.krillsson.sysapi.packageupdates

import com.krillsson.sysapi.config.PackageUpdateNotifyStyle
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.genericevents.GenericEventRepository
import com.krillsson.sysapi.core.genericevents.PackageUpdatesAvailable
import com.krillsson.sysapi.notifications.NotificationManager
import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@Service
class PackageUpdatesDigestNotifier(
    private val genericEventRepository: GenericEventRepository,
    private val notificationManager: NotificationManager,
    yamlConfigFile: YAMLConfigFile
) {
    private val logger by logger()

    private val config = yamlConfigFile.packageUpdates
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

        val event = genericEventRepository.read().filterIsInstance<PackageUpdatesAvailable>().firstOrNull()
        if (event == null) {
            logger.debug("No pending package updates to include in the daily digest")
            return
        }

        logger.info("Sending daily digest for {} pending package updates", event.totalCount)
        notificationManager.notify(event.asNotification())
    }

    private fun enabled() = config.enabled &&
            config.notify &&
            config.notifyStyle == PackageUpdateNotifyStyle.DAILY_DIGEST

    private fun digestAfter(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(digestAt)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
