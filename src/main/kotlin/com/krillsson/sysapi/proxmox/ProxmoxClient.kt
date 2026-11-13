package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.trustingCertificatesIn
import com.krillsson.sysapi.util.trustingEveryCertificate
import okhttp3.FormBody
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

    sealed interface Result<out T> {
        data class Ok<T>(val data: T) : Result<T>
        sealed interface Failure : Result<Nothing>
        data class Unauthorized(val reason: String) : Failure
        data class Forbidden(val reason: String) : Failure
        data class Failed(val status: Int, val reason: String) : Failure
        data class TransportFailure(val error: Exception) : Failure
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

    fun <T> get(path: String, type: Class<T>): Result<T> = get(path, objectMapper.constructType(type))

    fun <T> getList(path: String, elementType: Class<T>): Result<List<T>> =
        get(path, objectMapper.typeFactory.constructCollectionType(List::class.java, elementType))

    fun <T> post(path: String, form: Map<String, String>, type: Class<T>): Result<T> {
        val body = FormBody.Builder()
            .apply { form.forEach { (name, value) -> add(name, value) } }
            .build()
        return execute(requestTo(path).post(body), objectMapper.constructType(type))
    }

    private fun <T> get(path: String, type: JavaType): Result<T> = execute(requestTo(path).get(), type)

    private fun requestTo(path: String): Request.Builder = Request.Builder()
        .url(urlOf(path))
        .header("Authorization", "PVEAPIToken=${config.tokenId}=${config.secret}")
        .header("Accept", "application/json")

    private fun <T> execute(request: Request.Builder, type: JavaType): Result<T> {
        return try {
            client.newCall(request.build()).execute().use { resultOf(it, type) }
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

    private fun <T> resultOf(response: Response, type: JavaType): Result<T> {
        val body = response.body?.string().orEmpty()
        val json = runCatching { objectMapper.readTree(body) }.getOrNull()
        if (response.isSuccessful) {
            return dataOf(json, type, response.code)
        }
        val reason = reasonOf(response, json)
        return when (response.code) {
            UNAUTHORIZED -> Result.Unauthorized(reason)
            FORBIDDEN -> Result.Forbidden(reason)
            else -> Result.Failed(response.code, reason)
        }
    }

    private fun <T> dataOf(json: JsonNode?, type: JavaType, status: Int): Result<T> {
        val data = json?.get("data")?.takeIf { !it.isNull }
            ?: return Result.Failed(status, "Proxmox sent an answer without data")
        return try {
            Result.Ok(objectMapper.convertValue(data, type))
        } catch (exception: IllegalArgumentException) {
            val cause = (exception.cause as? JsonProcessingException)?.originalMessage ?: exception.message
            Result.Failed(status, "Proxmox sent an answer the agent could not read as ${type.rawClass.simpleName}: $cause")
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
