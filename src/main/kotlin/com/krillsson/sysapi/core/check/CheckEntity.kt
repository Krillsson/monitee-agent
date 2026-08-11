package com.krillsson.sysapi.core.check

import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import java.time.Instant
import java.util.*

@Entity
class CheckEntity(
    @Id
    val id: UUID,
    @Enumerated(EnumType.STRING)
    val type: CheckType,
    val name: String?,
    val enabled: Boolean,
    val intervalSeconds: Int,
    val timeoutSeconds: Int,
    val url: String? = null,
    @Enumerated(EnumType.STRING)
    val method: HttpMethod? = null,
    val expectedStatusCodes: String? = null,
    val keyword: String? = null,
    val keywordInverted: Boolean = false,
    val ignoreCertificateErrors: Boolean = false,
    val followRedirects: Boolean = false,
    @Convert(converter = HttpHeaderListJsonConverter::class)
    val headers: List<HttpHeader>? = null,
    val host: String? = null,
    val port: Int? = null
)

@Entity
class CheckResultEntity(
    @Id
    val id: UUID,
    val checkId: UUID,
    @Enumerated(EnumType.STRING)
    val checkType: CheckType,
    val timestamp: Instant,
    val successful: Boolean,
    val latencyMs: Long,
    val message: String,
    val responseCode: Int?,
    val errorBody: String?
)

enum class BucketResolution {
    HOURLY, DAILY
}

@Entity
class CheckResultBucketEntity(
    @Id
    val id: UUID,
    val checkId: UUID,
    @Enumerated(EnumType.STRING)
    val resolution: BucketResolution,
    val bucketStart: Instant,
    val samples: Int,
    val successful: Int,
    val failed: Int,
    val downtimeSeconds: Long,
    val minLatencyMs: Long,
    val avgLatencyMs: Long,
    val maxLatencyMs: Long,
    val p95LatencyMs: Long,
    val lastMessage: String?
)
