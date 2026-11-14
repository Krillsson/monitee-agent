package com.krillsson.sysapi.unraid

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.trustingCertificatesIn
import com.krillsson.sysapi.util.trustingEveryCertificate
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.springframework.stereotype.Component
import java.io.File
import java.util.concurrent.TimeUnit

@Component
class UnraidClient(
    configuration: YAMLConfigFile,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val UNAUTHORIZED = 401
        private const val FORBIDDEN = 403
        private const val TIMEOUT_SECONDS = 10L
        private val UNAUTHENTICATED_CODES = setOf("UNAUTHENTICATED", "UNAUTHORIZED")
        private const val FORBIDDEN_CODE = "FORBIDDEN"
        private val JSON = "application/json".toMediaType()
    }

    sealed interface Result<out T> {
        data class Ok<T>(val data: T) : Result<T>
        sealed interface Failure : Result<Nothing>
        data class Unauthorized(val reason: String) : Failure
        data class Forbidden(val reason: String) : Failure
        data class GraphQlErrors(val messages: List<String>, val codes: List<String>) : Failure
        data class Redirected(val status: Int, val location: String?) : Failure
        data class Failed(val status: Int, val reason: String) : Failure
        data class TransportFailure(val error: Exception) : Failure
    }

    private val config: UnraidConfiguration = configuration.unraid

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .apply {
                when {
                    !config.verifyTls -> trustingEveryCertificate()
                    config.caCertificatePath != null -> trustingCertificatesIn(File(config.caCertificatePath))
                }
            }
            .build()
    }

    private val commandClient: OkHttpClient by lazy {
        client.newBuilder()
            .readTimeout(config.commandTimeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(config.commandTimeoutSeconds, TimeUnit.SECONDS)
            .build()
    }

    private val url: HttpUrl by lazy {
        HttpUrl.Builder()
            .scheme(config.scheme)
            .host(config.host)
            .port(config.port)
            .addPathSegment("graphql")
            .build()
    }

    fun vmDomains(): Result<List<UnraidApi.VmDomain>> = when (val result = query(UnraidApi.VM_DOMAINS_QUERY)) {
        is Result.Ok -> domainsOf(result.data)
        is Result.Failure -> result
    }

    fun vmCommand(mutation: String, id: String): Result<Boolean> =
        when (val result = query(UnraidApi.vmMutation(mutation), mapOf("id" to id), commandClient)) {
            is Result.Ok -> result.data.path("vm").path(mutation).takeIf { it.isBoolean }
                ?.let { Result.Ok(it.booleanValue()) }
                ?: Result.Failed(200, "Unraid sent an answer without vm.$mutation")

            is Result.Failure -> result
        }

    private fun query(query: String, variables: Map<String, Any> = emptyMap(), httpClient: OkHttpClient = client): Result<JsonNode> {
        val body = when {
            variables.isEmpty() -> mapOf("query" to query)
            else -> mapOf("query" to query, "variables" to variables)
        }
        val request = Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey)
            .header("Accept", "application/json")
            .post(objectMapper.writeValueAsString(body).toRequestBody(JSON))
            .build()
        return try {
            httpClient.newCall(request).execute().use { resultOf(it) }
        } catch (exception: Exception) {
            Result.TransportFailure(exception)
        }
    }

    private fun resultOf(response: Response): Result<JsonNode> {
        val json = runCatching { objectMapper.readTree(response.body?.string().orEmpty()) }.getOrNull()
        val errors = json?.path("errors")?.takeIf { it.isArray }?.toList().orEmpty()
        val messages = errors.mapNotNull { error -> error.path("message").takeIf { it.isTextual }?.asText()?.trim() }
        val codes = errors.mapNotNull { error -> error.path("extensions").path("code").takeIf { it.isTextual }?.asText() }
        val data = json?.get("data")?.takeIf { !it.isNull }
        return when {
            response.code == UNAUTHORIZED || codes.any { it in UNAUTHENTICATED_CODES } ->
                Result.Unauthorized(reasonOf(response, json, messages))

            response.code == FORBIDDEN || FORBIDDEN_CODE in codes -> Result.Forbidden(reasonOf(response, json, messages))
            response.isRedirect -> Result.Redirected(response.code, response.header("Location"))
            errors.isNotEmpty() -> Result.GraphQlErrors(messages, codes)
            !response.isSuccessful -> Result.Failed(response.code, reasonOf(response, json, messages))
            data == null -> Result.Failed(response.code, "Unraid sent an answer without data")
            else -> Result.Ok(data)
        }
    }

    private fun domainsOf(data: JsonNode): Result<List<UnraidApi.VmDomain>> {
        val domains = data.path("vms").path("domains")
        val type = objectMapper.typeFactory.constructCollectionType(List::class.java, UnraidApi.VmDomain::class.java)
        return when {
            !domains.isArray -> Result.Failed(200, "Unraid sent an answer without vms.domains")
            else -> try {
                Result.Ok(objectMapper.convertValue(domains, type))
            } catch (exception: IllegalArgumentException) {
                val cause = (exception.cause as? JsonProcessingException)?.originalMessage ?: exception.message
                Result.Failed(200, "Unraid sent VMs the agent could not read: $cause")
            }
        }
    }

    private fun reasonOf(response: Response, json: JsonNode?, messages: List<String>): String {
        val message = json?.path("message")?.takeIf { it.isTextual }?.asText()?.trim()
        return listOf(messages.joinToString("; "), message, response.message.trim())
            .firstOrNull { !it.isNullOrBlank() }
            ?: "status ${response.code}"
    }
}
