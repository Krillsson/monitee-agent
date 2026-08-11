package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

@Component
class PingCheckProbe {

    companion object {
        private const val LOOPBACK_TIMEOUT_MS = 1000
    }

    private val logger by logger()

    // The JDK will not say whether it can send an ICMP echo. When it cannot it quietly connects to
    // TCP port 7 instead, which the loopback address refuses, so a reachable loopback means echo works.
    val icmpAvailable: Boolean by lazy {
        val available = try {
            InetAddress.getLoopbackAddress().isReachable(LOOPBACK_TIMEOUT_MS)
        } catch (e: Exception) {
            logger.debug("Unable to determine whether ICMP echo is available", e)
            false
        }
        if (!available) {
            logger.info("Ping checks will connect to TCP port 7 because this process cannot send ICMP echo requests")
        }
        available
    }

    fun probe(spec: PingCheckSpec): CheckResult {
        val start = Instant.now()
        return try {
            val address = InetAddress.getByName(spec.host)
            val reachable = address.isReachable(spec.timeoutSeconds * 1000)
            result(spec, start, reachable, if (reachable) "${address.hostAddress} answered" else noAnswer(spec))
        } catch (e: Exception) {
            result(spec, start, false, e.message ?: e::class.java.simpleName)
        }
    }

    private fun noAnswer(spec: PingCheckSpec) = if (icmpAvailable) {
        "${spec.host} did not answer within ${spec.timeoutSeconds}s"
    } else {
        "${spec.host} refused a connection on TCP port 7, which is what this agent falls back to without ICMP"
    }

    private fun result(spec: PingCheckSpec, start: Instant, successful: Boolean, message: String): CheckResult {
        val latencyMs = Duration.between(start, Instant.now()).toMillis()
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
            errorBody = null
        )
    }
}
