package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.config.CheckHistoryConfiguration
import com.krillsson.sysapi.config.HistoryConfiguration
import com.krillsson.sysapi.config.MetricsConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

val CHECK_ID: UUID = UUID.fromString("11111111-2222-3333-4444-555555555555")

fun at(timestamp: String): Instant = Instant.parse(timestamp)

fun checkResult(
    timestamp: Instant,
    successful: Boolean = true,
    latencyMs: Long = 100,
    message: String = if (successful) "OK" else "Connection refused",
    checkId: UUID = CHECK_ID
) = CheckResultEntity(
    id = UUID.randomUUID(),
    checkId = checkId,
    checkType = CheckType.HTTP,
    timestamp = timestamp,
    successful = successful,
    latencyMs = latencyMs,
    message = message,
    responseCode = if (successful) 200 else null,
    errorBody = null
)

fun bucket(
    resolution: BucketResolution,
    bucketStart: Instant,
    samples: Int,
    successful: Int,
    downtimeSeconds: Long = 0,
    minLatencyMs: Long = 10,
    avgLatencyMs: Long = 50,
    maxLatencyMs: Long = 100,
    p95LatencyMs: Long = 90,
    lastMessage: String? = "OK",
    checkId: UUID = CHECK_ID
) = CheckResultBucketEntity(
    id = UUID.randomUUID(),
    checkId = checkId,
    resolution = resolution,
    bucketStart = bucketStart,
    samples = samples,
    successful = successful,
    failed = samples - successful,
    downtimeSeconds = downtimeSeconds,
    minLatencyMs = minLatencyMs,
    avgLatencyMs = avgLatencyMs,
    maxLatencyMs = maxLatencyMs,
    p95LatencyMs = p95LatencyMs,
    lastMessage = lastMessage
)

fun httpCheckEntity(
    id: UUID = CHECK_ID,
    name: String? = "Example",
    enabled: Boolean = true,
    intervalSeconds: Int = 60,
    timeoutSeconds: Int = 20,
    url: String = "https://example.com",
    method: HttpMethod = HttpMethod.GET,
    expectedStatusCodes: String = "200-299",
    keyword: String? = null,
    keywordInverted: Boolean = false,
    ignoreCertificateErrors: Boolean = false,
    followRedirects: Boolean = true,
    headers: List<HttpHeader>? = null
) = CheckEntity(
    id = id,
    type = CheckType.HTTP,
    name = name,
    enabled = enabled,
    intervalSeconds = intervalSeconds,
    timeoutSeconds = timeoutSeconds,
    url = url,
    method = method,
    expectedStatusCodes = expectedStatusCodes,
    keyword = keyword,
    keywordInverted = keywordInverted,
    ignoreCertificateErrors = ignoreCertificateErrors,
    followRedirects = followRedirects,
    headers = headers
)

fun httpCheckSpec(
    name: String? = "Example",
    enabled: Boolean = true,
    intervalSeconds: Int = 60,
    timeoutSeconds: Int = 20,
    url: String = "https://example.com",
    method: HttpMethod = HttpMethod.GET,
    expectedStatusCodes: String = "200-299",
    keyword: String? = null,
    keywordInverted: Boolean = false,
    ignoreCertificateErrors: Boolean = false,
    followRedirects: Boolean = true,
    headers: List<HttpHeader> = emptyList()
) = HttpCheckSpec(
    name = name,
    enabled = enabled,
    intervalSeconds = intervalSeconds,
    timeoutSeconds = timeoutSeconds,
    url = url,
    method = method,
    expectedStatusCodes = expectedStatusCodes,
    keyword = keyword,
    keywordInverted = keywordInverted,
    ignoreCertificateErrors = ignoreCertificateErrors,
    followRedirects = followRedirects,
    headers = headers
)

fun configWithCheckRetention(checks: CheckHistoryConfiguration = CheckHistoryConfiguration()): YAMLConfigFile {
    val config = mockk<YAMLConfigFile>()
    every { config.metricsConfig } returns MetricsConfiguration(history = HistoryConfiguration(checks = checks))
    return config
}
