package com.krillsson.sysapi.docker.updates

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.RegistryConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.util.logger
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Reads the digest a registry currently serves for a tag.
 *
 * A `HEAD` on the manifest endpoint answers with the digest in the `Docker-Content-Digest`
 * header without transferring the manifest, and unlike a `GET` it is not counted against
 * Docker Hub's anonymous pull limit.
 */
@Component
class RegistryClient(
    yamlConfigFile: YAMLConfigFile,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private const val DIGEST_HEADER = "Docker-Content-Digest"
        private const val CHALLENGE_HEADER = "WWW-Authenticate"
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val UNAUTHORIZED = 401
        private const val NOT_FOUND = 404
        private val ACCEPTED_MANIFEST_TYPES = listOf(
            "application/vnd.oci.image.index.v1+json",
            "application/vnd.docker.distribution.manifest.list.v2+json",
            "application/vnd.oci.image.manifest.v1+json",
            "application/vnd.docker.distribution.manifest.v2+json"
        ).joinToString(",")
        private val TOKEN_EXPIRY_MARGIN = Duration.ofSeconds(10)
        private val DEFAULT_TOKEN_LIFETIME = Duration.ofSeconds(60)
    }

    sealed interface DigestResult {
        data class Found(val digest: String) : DigestResult

        /**
         * The registry has nothing to compare against: the image was built locally, or the
         * repository is private and no credentials are configured for it.
         */
        data class NotComparable(val reason: String) : DigestResult
        data class Failed(val reason: String) : DigestResult
    }

    data class RegistryCredentials(val username: String, val password: String)

    private data class CachedAuthorization(val header: String, val expiresAt: Instant)

    private val logger by logger()

    private val registries: List<RegistryConfiguration> = yamlConfigFile.docker.updateCheck.registries

    private val client = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val authorizations = ConcurrentHashMap<String, CachedAuthorization>()

    fun remoteDigest(reference: ImageReference): DigestResult {
        val url = "https://${reference.apiHost}/v2/${reference.repository}/manifests/${reference.tag}".toHttpUrlOrNull()
            ?: return DigestResult.Failed("$reference cannot be expressed as a registry URL")

        return try {
            val challenge = client.newCall(manifestRequest(url, cachedAuthorization(reference))).execute()
                .use { response ->
                    if (response.code != UNAUTHORIZED) {
                        return digestOf(response, reference)
                    }
                    response.header(CHALLENGE_HEADER)
                }

            val authorization = authorize(reference, challenge) ?: return unauthorized(reference)
            client.newCall(manifestRequest(url, authorization)).execute().use { response ->
                digestOf(response, reference)
            }
        } catch (exception: Exception) {
            DigestResult.Failed(exception.message ?: exception::class.java.simpleName)
        }
    }

    private fun manifestRequest(url: HttpUrl, authorization: String?) = Request.Builder()
        .url(url)
        .head()
        .header("Accept", ACCEPTED_MANIFEST_TYPES)
        .apply { authorization?.let { header(AUTHORIZATION_HEADER, it) } }
        .build()

    private fun digestOf(response: Response, reference: ImageReference): DigestResult {
        return when {
            response.isSuccessful -> response.header(DIGEST_HEADER)
                ?.let { DigestResult.Found(it) }
                ?: DigestResult.Failed("${reference.apiHost} replied without a $DIGEST_HEADER header")

            response.code == NOT_FOUND -> DigestResult.NotComparable(
                "${reference.repository}:${reference.tag} is not published on ${reference.apiHost}"
            )

            response.code == UNAUTHORIZED -> unauthorized(reference)
            else -> DigestResult.Failed("${reference.apiHost} replied with status ${response.code}")
        }
    }

    /**
     * Without credentials a registry answers the same way for a repository that does not exist
     * as for one the caller may not read, so an image that was built locally is indistinguishable
     * from a private one. Only a rejected set of configured credentials is a real failure.
     */
    private fun unauthorized(reference: ImageReference): DigestResult {
        return if (credentialsFor(reference) != null) {
            DigestResult.Failed("${reference.apiHost} rejected the configured credentials for ${reference.repository}")
        } else {
            DigestResult.NotComparable("${reference.repository} is not publicly readable on ${reference.apiHost}")
        }
    }

    private fun authorize(reference: ImageReference, challenge: String?): String? {
        return when {
            challenge == null -> null
            challenge.startsWith("Bearer", ignoreCase = true) -> requestToken(reference, challenge)
            challenge.startsWith("Basic", ignoreCase = true) -> basicAuthorization(reference)
            else -> null
        }
    }

    private fun cachedAuthorization(reference: ImageReference): String? {
        val key = cacheKey(reference)
        val cached = authorizations[key] ?: return null
        return if (cached.expiresAt.isAfter(Instant.now())) {
            cached.header
        } else {
            authorizations.remove(key)
            null
        }
    }

    private fun requestToken(reference: ImageReference, challenge: String): String? {
        val parameters = parseChallenge(challenge)
        val realm = parameters["realm"]?.toHttpUrlOrNull() ?: return null
        val url = realm.newBuilder()
            .apply {
                parameters["service"]?.let { addQueryParameter("service", it) }
                addQueryParameter("scope", parameters["scope"] ?: "repository:${reference.repository}:pull")
            }
            .build()

        val request = Request.Builder()
            .url(url)
            .apply {
                basicAuthorization(reference)?.let { header(AUTHORIZATION_HEADER, it) }
            }
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                logger.debug("Token request to {} replied {}", url.host, response.code)
                return@use null
            }
            val body = objectMapper.readTree(response.body?.byteStream() ?: return@use null)
            val token = listOf("token", "access_token")
                .map { body.path(it) }
                .firstOrNull { it.isTextual }
                ?.asText()
                ?: return@use null

            val lifetime = body.path("expires_in").asLong(DEFAULT_TOKEN_LIFETIME.seconds)
            "Bearer $token".also {
                authorizations[cacheKey(reference)] = CachedAuthorization(
                    it,
                    Instant.now().plusSeconds(lifetime).minus(TOKEN_EXPIRY_MARGIN)
                )
            }
        }
    }

    private fun basicAuthorization(reference: ImageReference): String? {
        return credentialsFor(reference)?.let { Credentials.basic(it.username, it.password) }
    }

    fun credentialsFor(reference: ImageReference): RegistryCredentials? {
        return registries.firstOrNull {
            it.host.equals(reference.registry, ignoreCase = true) ||
                    it.host.equals(reference.apiHost, ignoreCase = true)
        }?.let { RegistryCredentials(it.username, it.password) }
    }

    private fun cacheKey(reference: ImageReference) = "${reference.apiHost}/${reference.repository}"

    private fun parseChallenge(challenge: String): Map<String, String> {
        val parameters = challenge.substringAfter("Bearer ", "").takeIf { it.isNotEmpty() } ?: return emptyMap()
        return parameters.split(',')
            .mapNotNull { parameter ->
                val separator = parameter.indexOf('=')
                if (separator <= 0) {
                    null
                } else {
                    parameter.take(separator).trim() to parameter.substring(separator + 1).trim().trim('"')
                }
            }
            .toMap()
    }
}
