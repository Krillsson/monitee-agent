package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToLong

@Component
class PingCheckProbe(private val ping: Ping) {

    companion object {
        private const val LOOPBACK_ADDRESS = "127.0.0.1"
        private const val LOOPBACK_TIMEOUT_SECONDS = 1
    }

    private val logger by logger()

    val unavailable: String? by lazy {
        whyUnavailable().also { reason ->
            if (reason != null) {
                logger.info("Ping checks are unavailable: {}", reason)
            }
        }
    }

    val available: Boolean
        get() = unavailable == null

    fun availability() = PingAvailability(available = available, unavailableReason = unavailable)

    fun probe(spec: PingCheckSpec): CheckResult {
        val start = Instant.now()
        unavailable?.let { return result(spec, start, false, it, null) }
        val output = ping.run(spec.host, spec.timeoutSeconds)
            .getOrElse { return result(spec, start, false, it.message ?: it::class.java.simpleName, null) }
        val roundTripMs = Ping.roundTripMillis(output)?.roundToLong()
        return if (roundTripMs == null) {
            result(spec, start, false, noAnswer(spec, output), null)
        } else {
            result(spec, start, true, "${spec.host} answered", roundTripMs)
        }
    }

    private fun whyUnavailable(): String? = when {
        !ping.supported -> "this agent has no ${Ping.COMMAND} command for ${ping.platform}"
        !ping.installed() -> "the ${Ping.COMMAND} command was not found on this system"
        !loopbackAnswers() -> "the ${Ping.COMMAND} command cannot send ICMP echo requests, " +
                "which needs either root, CAP_NET_RAW or a net.ipv4.ping_group_range covering this process"

        else -> null
    }

    private fun loopbackAnswers(): Boolean {
        val output = ping.run(LOOPBACK_ADDRESS, LOOPBACK_TIMEOUT_SECONDS).getOrNull() ?: return false
        return Ping.roundTripMillis(output) != null
    }

    private fun noAnswer(spec: PingCheckSpec, output: String) =
        output.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }
            ?: "${spec.host} did not answer within ${spec.timeoutSeconds}s"

    private fun result(
        spec: PingCheckSpec,
        start: Instant,
        successful: Boolean,
        message: String,
        roundTripMs: Long?
    ): CheckResult {
        val latencyMs = roundTripMs ?: Duration.between(start, Instant.now()).toMillis()
        logger.debug("Check {} {}: {} ({}ms)", spec.host, if (successful) "SUCCESS" else "FAIL", message, latencyMs)
        return CheckResult(
            id = null,
            checkId = null,
            checkType = CheckType.PING,
            timestamp = Instant.now(),
            successful = successful,
            latencyMs = latencyMs,
            message = message,
            responseCode = null,
            errorBody = null,
            resolvedValues = null
        )
    }
}
