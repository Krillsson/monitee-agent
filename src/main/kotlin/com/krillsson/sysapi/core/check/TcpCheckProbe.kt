package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Duration
import java.time.Instant

@Component
class TcpCheckProbe {

    private val logger by logger()

    fun probe(spec: TcpCheckSpec): CheckResult {
        val start = Instant.now()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(spec.host, spec.port), spec.timeoutSeconds * 1000)
            }
            result(spec, start, true, "Connected to ${spec.host}:${spec.port}")
        } catch (e: Exception) {
            result(spec, start, false, e.message ?: e::class.java.simpleName)
        }
    }

    private fun result(spec: TcpCheckSpec, start: Instant, successful: Boolean, message: String): CheckResult {
        val latencyMs = Duration.between(start, Instant.now()).toMillis()
        logger.debug(
            "Check {}:{} {}: {} ({}ms)",
            spec.host,
            spec.port,
            if (successful) "SUCCESS" else "FAIL",
            message,
            latencyMs
        )
        return CheckResult(
            id = null,
            checkId = null,
            checkType = CheckType.TCP,
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
