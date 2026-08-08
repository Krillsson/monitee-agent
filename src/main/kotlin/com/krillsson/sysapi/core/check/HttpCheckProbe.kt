package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.util.logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Component
class HttpCheckProbe {

    companion object {
        private const val ERROR_BODY_LIMIT = 512L
        private const val KEYWORD_BODY_LIMIT = 1024L * 1024L
    }

    private val logger by logger()

    private val client = OkHttpClient.Builder().build()
    private val permissiveClient by lazy { client.newBuilder().trustingEveryCertificate() }

    fun probe(spec: HttpCheckSpec): CheckResult {
        val start = Instant.now()
        return try {
            val request = Request.Builder()
                .url(spec.url)
                .method(spec.method.name, spec.method.emptyBody())
                .apply { spec.headers.forEach { addHeader(it.name, it.value) } }
                .build()
            clientFor(spec).newCall(request).execute().use { response ->
                val latencyMs = Duration.between(start, Instant.now()).toMillis()
                evaluate(spec, response, latencyMs).also {
                    logger.debug(
                        "Check {} {}: {} - {} ({}ms)",
                        spec.url,
                        if (it.successful) "SUCCESS" else "FAIL",
                        response.code,
                        it.message,
                        latencyMs
                    )
                }
            }
        } catch (e: Exception) {
            val message = e.message ?: e::class.java.simpleName
            val latencyMs = Duration.between(start, Instant.now()).toMillis()
            logger.debug("Check {} FAIL: {} ({}ms)", spec.url, message, latencyMs)
            CheckResult(
                id = null,
                checkId = null,
                checkType = CheckType.HTTP,
                timestamp = Instant.now(),
                successful = false,
                latencyMs = latencyMs,
                message = message,
                responseCode = -1,
                errorBody = null
            )
        }
    }

    private fun evaluate(spec: HttpCheckSpec, response: Response, latencyMs: Long): CheckResult {
        val body = response.readLimitedBody(if (spec.keyword != null) KEYWORD_BODY_LIMIT else ERROR_BODY_LIMIT)
        val statusMatches = StatusCodeRanges.parse(spec.expectedStatusCodes)?.matches(response.code) ?: false
        val keywordMatches = spec.keyword?.let { body.orEmpty().contains(it) != spec.keywordInverted } ?: true
        val message = when {
            !statusMatches -> "Expected status ${spec.expectedStatusCodes} but got ${response.code}"
            !keywordMatches && spec.keywordInverted -> "Body contains \"${spec.keyword}\""
            !keywordMatches -> "Body does not contain \"${spec.keyword}\""
            else -> response.message.ifBlank { "OK" }
        }
        return CheckResult(
            id = null,
            checkId = null,
            checkType = CheckType.HTTP,
            timestamp = Instant.now(),
            successful = statusMatches && keywordMatches,
            latencyMs = latencyMs,
            message = message,
            responseCode = response.code,
            errorBody = body?.take(ERROR_BODY_LIMIT.toInt())
        )
    }

    private fun clientFor(spec: HttpCheckSpec): OkHttpClient {
        val timeout = spec.timeoutSeconds.toLong()
        return (if (spec.ignoreCertificateErrors) permissiveClient else client)
            .newBuilder()
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .callTimeout(timeout, TimeUnit.SECONDS)
            .followRedirects(spec.followRedirects)
            .followSslRedirects(spec.followRedirects)
            .build()
    }

    private fun HttpMethod.emptyBody() = if (this == HttpMethod.POST) ByteArray(0).toRequestBody() else null

    private fun Response.readLimitedBody(byteCount: Long): String? {
        return try {
            peekBody(byteCount).use { it.string() }
        } catch (exception: Exception) {
            "${exception::class.simpleName}: ${exception.message}"
        }
    }

    private fun OkHttpClient.Builder.trustingEveryCertificate(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }
        return sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }
}
