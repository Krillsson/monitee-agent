package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.trustingCertificatesIn
import com.krillsson.sysapi.util.trustingEveryCertificate
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
class ProxmoxClient(
    configuration: YAMLConfigFile,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val TIMEOUT_SECONDS = 10L
    }

    sealed interface Result {
        data class Ok(val data: JsonNode) : Result
        data class Unauthorized(val reason: String) : Result
        data class Forbidden(val reason: String) : Result
        data class Failed(val status: Int, val reason: String) : Result
        data class TransportFailure(val error: Exception) : Result
    }

    private val config: ProxmoxConfiguration = configuration.proxmox

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // Proxmox puts the error text of a failed call in the HTTP/1.1 reason phrase, which HTTP/2 does not have.
            .protocols(listOf(Protocol.HTTP_1_1))
            .apply {
                when {
                    !config.verifyTls -> trustingEveryCertificate()
                    config.caCertificatePath != null -> trustingCertificatesIn(File(config.caCertificatePath))
                }
            }
            .build()
    }

    fun get(path: String): Result {
        return try {
            val request = Request.Builder()
                .url(urlOf(path))
                .header("Authorization", "PVEAPIToken=${config.tokenId}=${config.secret}")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resultOf(it) }
        } catch (exception: Exception) {
            Result.TransportFailure(exception)
        }
    }

    private fun urlOf(path: String): HttpUrl = HttpUrl.Builder()
        .scheme("https")
        .host(config.host)
        .port(config.port)
        .addPathSegments("api2/json")
        .addPathSegments(path.trimStart('/'))
        .build()

    private fun resultOf(response: Response): Result {
        val body = response.body?.string().orEmpty()
        val json = runCatching { objectMapper.readTree(body) }.getOrNull()
        if (response.isSuccessful) {
            return Result.Ok(json?.path("data") ?: objectMapper.nullNode())
        }
        val reason = reasonOf(response, json)
        return when (response.code) {
            UNAUTHORIZED -> Result.Unauthorized(reason)
            FORBIDDEN -> Result.Forbidden(reason)
            else -> Result.Failed(response.code, reason)
        }
    }

    private fun reasonOf(response: Response, json: JsonNode?): String {
        val message = json?.path("message")?.takeIf { it.isTextual }?.asText()?.trim()
        val errors = json?.path("errors")
            ?.takeIf { it.isObject }
            ?.fields()
            ?.asSequence()
            ?.joinToString("; ") { (field, error) -> "$field: ${error.asText().trim()}" }
        return listOf(message, errors, response.message.trim())
            .firstOrNull { !it.isNullOrBlank() }
            ?: "status ${response.code}"
    }
}
