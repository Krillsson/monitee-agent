package com.krillsson.sysapi.workload

import com.krillsson.sysapi.util.logger
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Component
class PowerCommandConfirmation(
    private val taskScheduler: TaskScheduler,
    private val clock: Clock
) {
    companion object {
        val CHECK_INTERVAL: Duration = Duration.ofSeconds(2)
    }

    private val logger by logger()

    fun await(timeout: Duration, reached: () -> Boolean, done: (Boolean) -> Unit) {
        check(clock.instant().plus(timeout), reached, done)
    }

    private fun check(deadline: Instant, reached: () -> Boolean, done: (Boolean) -> Unit) {
        val isReached = try {
            reached()
        } catch (exception: Exception) {
            logger.warn("Failed to read the state after a power command", exception)
            false
        }
        when {
            isReached -> done(true)
            !clock.instant().isBefore(deadline) -> done(false)
            else -> taskScheduler.schedule({ check(deadline, reached, done) }, clock.instant().plus(CHECK_INTERVAL))
        }
    }
}
