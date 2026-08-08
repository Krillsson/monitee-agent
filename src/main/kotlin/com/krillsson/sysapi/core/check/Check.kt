package com.krillsson.sysapi.core.check

import java.time.Instant
import java.util.*

enum class CheckType {
    HTTP
}

enum class HttpMethod {
    GET, HEAD, POST
}

data class HttpHeader(
    val name: String,
    val value: String
)

sealed interface Check {
    val id: UUID
    val type: CheckType
    val name: String
    val enabled: Boolean
    val intervalSeconds: Int
    val timeoutSeconds: Int
}

data class HttpCheck(
    override val id: UUID,
    override val name: String,
    override val enabled: Boolean,
    override val intervalSeconds: Int,
    override val timeoutSeconds: Int,
    val url: String,
    val method: HttpMethod,
    val expectedStatusCodes: String,
    val keyword: String?,
    val keywordInverted: Boolean,
    val ignoreCertificateErrors: Boolean,
    val followRedirects: Boolean,
    val headers: List<HttpHeader>
) : Check {
    override val type = CheckType.HTTP
}

data class HttpCheckSpec(
    val name: String?,
    val enabled: Boolean,
    val intervalSeconds: Int,
    val timeoutSeconds: Int,
    val url: String,
    val method: HttpMethod,
    val expectedStatusCodes: String,
    val keyword: String?,
    val keywordInverted: Boolean,
    val ignoreCertificateErrors: Boolean,
    val followRedirects: Boolean,
    val headers: List<HttpHeader>
)

data class CheckResult(
    val id: UUID?,
    val checkId: UUID?,
    val checkType: CheckType,
    val timestamp: Instant,
    val successful: Boolean,
    val latencyMs: Long,
    val message: String,
    val responseCode: Int?,
    val errorBody: String?
)

enum class CheckResolution {
    AUTO, RAW, HOURLY, DAILY
}

data class CheckHistory(
    val resolution: CheckResolution,
    val from: Instant,
    val to: Instant,
    val points: List<CheckHistoryPoint>
)

data class CheckHistoryPoint(
    val timestamp: Instant,
    val samples: Int,
    val successful: Int,
    val failed: Int,
    val uptimePercent: Double,
    val downtimeSeconds: Long,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val p95LatencyMs: Long
)

data class CheckUptime(
    val from: Instant,
    val to: Instant,
    val samples: Int,
    val successful: Int,
    val failed: Int,
    val uptimePercent: Double,
    val downtimeSeconds: Long,
    val totalSeconds: Long
)

sealed interface CreateCheckResult {
    data class Success(val id: UUID) : CreateCheckResult
    data class Fail(val reason: String) : CreateCheckResult
}

sealed interface UpdateCheckResult {
    data class Success(val id: UUID) : UpdateCheckResult
    data class Fail(val reason: String) : UpdateCheckResult
}

sealed interface RunCheckResult {
    data class Success(val result: CheckResult) : RunCheckResult
    data class Fail(val reason: String) : RunCheckResult
}

data class UptimeMetrics(
    val total: UptimePeriod,
    val perDay: List<UptimeDay>
)

data class UptimePeriod(
    val periodStart: Instant,
    val periodEnd: Instant,
    val totalDownTimeSeconds: Long,
    val totalUptimePercent: Double,
    val totalSeconds: Long
)

data class UptimeDay(
    val timestampAtStartOfDay: Instant,
    val uptimePercent: Double,
    val downTimeSeconds: Long,
    val totalSeconds: Long
)
