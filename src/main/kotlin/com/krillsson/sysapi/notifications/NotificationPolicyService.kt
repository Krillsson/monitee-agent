package com.krillsson.sysapi.notifications

import com.krillsson.sysapi.notifications.NotificationPolicyStore.StoredNotificationPolicy
import com.krillsson.sysapi.notifications.NotificationPolicyStore.StoredNotificationPolicy.StoredQuietHours
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

@Service
class NotificationPolicyService(
    private val store: NotificationPolicyStore,
    private val clock: Clock
) {
    @Volatile
    private var stored: StoredNotificationPolicy? = null

    private val suppressedCounts = ConcurrentHashMap<SuppressionReason, AtomicInteger>()

    fun policy(): NotificationPolicy {
        val settings = settings()
        return NotificationPolicy(
            quietHours = settings.quietHours.asQuietHours(),
            cooldownMinutes = settings.cooldownMinutes,
            notifyOnRecovery = settings.notifyOnRecovery,
            suppressed = SuppressedNotificationCounts(
                acknowledged = suppressedCount(SuppressionReason.ACKNOWLEDGED),
                cooldown = suppressedCount(SuppressionReason.COOLDOWN),
                quietHours = suppressedCount(SuppressionReason.QUIET_HOURS),
                recovery = suppressedCount(SuppressionReason.RECOVERY)
            )
        )
    }

    fun settings(): NotificationPolicySettings {
        val stored = stored()
        val quietHours = stored.quietHours ?: StoredQuietHours()
        return NotificationPolicySettings(
            quietHours = QuietHoursSettings(
                enabled = quietHours.enabled ?: false,
                start = quietHours.start?.let { LocalTime.parse(it, TIME_FORMAT) } ?: DEFAULT_QUIET_HOURS_START,
                end = quietHours.end?.let { LocalTime.parse(it, TIME_FORMAT) } ?: DEFAULT_QUIET_HOURS_END,
                days = quietHours.days?.toSet() ?: DayOfWeek.entries.toSet(),
                timeZone = quietHours.timeZone?.let { ZoneId.of(it) } ?: ZoneId.systemDefault(),
                allowCritical = quietHours.allowCritical ?: false
            ),
            cooldownMinutes = stored.cooldownMinutes ?: DEFAULT_COOLDOWN_MINUTES,
            notifyOnRecovery = stored.notifyOnRecovery ?: true
        )
    }

    fun isQuietHoursActive(): Boolean = settings().quietHours.isActive()

    fun update(update: NotificationPolicyUpdate): NotificationPolicy {
        val quietHours = update.quietHours
        val start = quietHours?.start?.let { parseTime(it, "start") }
        val end = quietHours?.end?.let { parseTime(it, "end") }
        val timeZone = quietHours?.timeZone?.let { parseZone(it) }
        val current = settings().quietHours
        validate(quietHours?.days?.isEmpty() != true) { "Quiet hours need at least one day" }
        validate((start ?: current.start) != (end ?: current.end)) { "Quiet hours cannot start and end at the same time" }
        validate(update.cooldownMinutes == null || update.cooldownMinutes >= 0) { "cooldownMinutes cannot be negative" }

        val previous = stored()
        val previousQuietHours = previous.quietHours ?: StoredQuietHours()
        val updated = StoredNotificationPolicy(
            quietHours = StoredQuietHours(
                enabled = quietHours?.enabled ?: previousQuietHours.enabled,
                start = start?.format(TIME_FORMAT) ?: previousQuietHours.start,
                end = end?.format(TIME_FORMAT) ?: previousQuietHours.end,
                days = quietHours?.days?.sorted() ?: previousQuietHours.days,
                timeZone = timeZone?.id ?: previousQuietHours.timeZone,
                allowCritical = quietHours?.allowCritical ?: previousQuietHours.allowCritical
            ),
            cooldownMinutes = update.cooldownMinutes ?: previous.cooldownMinutes,
            notifyOnRecovery = update.notifyOnRecovery ?: previous.notifyOnRecovery
        )
        store.write(updated)
        stored = updated
        return policy()
    }

    fun recordSuppressed(reason: SuppressionReason) {
        suppressedCounts.computeIfAbsent(reason) { AtomicInteger(0) }.incrementAndGet()
    }

    private fun suppressedCount(reason: SuppressionReason) = suppressedCounts[reason]?.get() ?: 0

    private fun QuietHoursSettings.isActive(): Boolean {
        val now = clock.instant().atZone(timeZone)
        val time = now.toLocalTime()
        val today = now.dayOfWeek
        return when {
            !enabled -> false
            start < end -> today in days && time >= start && time < end
            else -> (today in days && time >= start) || (today.minus(1) in days && time < end)
        }
    }

    private fun QuietHoursSettings.asQuietHours() = QuietHours(
        enabled = enabled,
        start = start.format(TIME_FORMAT),
        end = end.format(TIME_FORMAT),
        days = days.sorted(),
        timeZone = timeZone.id,
        allowCritical = allowCritical,
        active = isActive()
    )

    private fun parseTime(value: String, field: String): LocalTime = try {
        LocalTime.parse(value, TIME_FORMAT)
    } catch (e: DateTimeParseException) {
        throw InvalidNotificationPolicyException("Quiet hours $field must be a time as HH:mm, was '$value'")
    }

    private fun parseZone(value: String): ZoneId = try {
        ZoneId.of(value)
    } catch (e: DateTimeException) {
        throw InvalidNotificationPolicyException("Unknown time zone '$value'")
    }

    private fun validate(condition: Boolean, message: () -> String) {
        if (!condition) throw InvalidNotificationPolicyException(message())
    }

    private fun stored(): StoredNotificationPolicy =
        stored ?: (store.read() ?: StoredNotificationPolicy()).also { stored = it }

    companion object {
        private const val DEFAULT_COOLDOWN_MINUTES = 5
        private val DEFAULT_QUIET_HOURS_START: LocalTime = LocalTime.of(22, 0)
        private val DEFAULT_QUIET_HOURS_END: LocalTime = LocalTime.of(7, 0)
        private val TIME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
    }
}

enum class SuppressionReason(val description: String) {
    ACKNOWLEDGED("the event is acknowledged"),
    COOLDOWN("the monitor is in its cooldown"),
    QUIET_HOURS("quiet hours are active"),
    RECOVERY("recovery notifications are turned off")
}

class InvalidNotificationPolicyException(message: String) : IllegalArgumentException(message)

data class NotificationPolicySettings(
    val quietHours: QuietHoursSettings,
    val cooldownMinutes: Int,
    val notifyOnRecovery: Boolean
)

data class QuietHoursSettings(
    val enabled: Boolean,
    val start: LocalTime,
    val end: LocalTime,
    val days: Set<DayOfWeek>,
    val timeZone: ZoneId,
    val allowCritical: Boolean
)

data class NotificationPolicyUpdate(
    val quietHours: QuietHoursUpdate? = null,
    val cooldownMinutes: Int? = null,
    val notifyOnRecovery: Boolean? = null
)

data class QuietHoursUpdate(
    val enabled: Boolean? = null,
    val start: String? = null,
    val end: String? = null,
    val days: List<DayOfWeek>? = null,
    val timeZone: String? = null,
    val allowCritical: Boolean? = null
)

data class NotificationPolicy(
    val quietHours: QuietHours,
    val cooldownMinutes: Int,
    val notifyOnRecovery: Boolean,
    val suppressed: SuppressedNotificationCounts
)

data class QuietHours(
    val enabled: Boolean,
    val start: String,
    val end: String,
    val days: List<DayOfWeek>,
    val timeZone: String,
    val allowCritical: Boolean,
    val active: Boolean
)

data class SuppressedNotificationCounts(
    val acknowledged: Int,
    val cooldown: Int,
    val quietHours: Int,
    val recovery: Int
)
