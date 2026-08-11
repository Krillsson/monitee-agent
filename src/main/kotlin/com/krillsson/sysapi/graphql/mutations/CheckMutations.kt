package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.check.CheckResult
import com.krillsson.sysapi.core.check.DnsRecordType
import com.krillsson.sysapi.core.check.HttpMethod
import java.util.*

data class HttpHeaderInput(
    val name: String,
    val value: String
)

data class CreateHttpCheckInput(
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val url: String,
    val method: HttpMethod?,
    val expectedStatusCodes: String?,
    val keyword: String?,
    val keywordInverted: Boolean?,
    val ignoreCertificateErrors: Boolean?,
    val followRedirects: Boolean?,
    val headers: List<HttpHeaderInput>?
)

data class UpdateHttpCheckInput(
    val id: UUID,
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val url: String,
    val method: HttpMethod?,
    val expectedStatusCodes: String?,
    val keyword: String?,
    val keywordInverted: Boolean?,
    val ignoreCertificateErrors: Boolean?,
    val followRedirects: Boolean?,
    val headers: List<HttpHeaderInput>?
)

data class OneOffHttpCheckInput(
    val url: String,
    val method: HttpMethod?,
    val expectedStatusCodes: String?,
    val keyword: String?,
    val keywordInverted: Boolean?,
    val ignoreCertificateErrors: Boolean?,
    val followRedirects: Boolean?,
    val timeoutSeconds: Int?,
    val headers: List<HttpHeaderInput>?
)

data class CreateTcpCheckInput(
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val host: String,
    val port: Int
)

data class UpdateTcpCheckInput(
    val id: UUID,
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val host: String,
    val port: Int
)

data class OneOffTcpCheckInput(
    val host: String,
    val port: Int,
    val timeoutSeconds: Int?
)

data class CreatePingCheckInput(
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val host: String
)

data class UpdatePingCheckInput(
    val id: UUID,
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val host: String
)

data class OneOffPingCheckInput(
    val host: String,
    val timeoutSeconds: Int?
)

data class CreateDnsCheckInput(
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val hostname: String,
    val resolver: String?,
    val recordType: DnsRecordType?,
    val expectedValues: List<String>?
)

data class UpdateDnsCheckInput(
    val id: UUID,
    val name: String?,
    val enabled: Boolean?,
    val intervalSeconds: Int?,
    val timeoutSeconds: Int?,
    val hostname: String,
    val resolver: String?,
    val recordType: DnsRecordType?,
    val expectedValues: List<String>?
)

data class OneOffDnsCheckInput(
    val hostname: String,
    val resolver: String?,
    val recordType: DnsRecordType?,
    val expectedValues: List<String>?,
    val timeoutSeconds: Int?
)

data class DeleteCheckInput(val id: UUID)

data class SetCheckEnabledInput(val id: UUID, val enabled: Boolean)

data class RunCheckNowInput(val id: UUID)

interface CreateCheckOutput

data class CreateCheckSuccess(val id: UUID) : CreateCheckOutput

data class CreateCheckFailed(val reason: String) : CreateCheckOutput

interface UpdateCheckOutput

data class UpdateCheckSuccess(val id: UUID) : UpdateCheckOutput

data class UpdateCheckFailed(val reason: String) : UpdateCheckOutput

data class DeleteCheckOutput(val removed: Boolean)

interface RunCheckNowOutput

data class RunCheckNowSuccess(val result: CheckResult) : RunCheckNowOutput

data class RunCheckNowFailed(val reason: String) : RunCheckNowOutput
