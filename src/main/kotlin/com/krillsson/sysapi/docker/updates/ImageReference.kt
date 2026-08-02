package com.krillsson.sysapi.docker.updates

/**
 * A container image reference split into the parts the registry API needs.
 *
 * `nginx` resolves to `docker.io/library/nginx:latest`, `ghcr.io/user/app` to
 * `ghcr.io/user/app:latest` and `lscr.io/linuxserver/radarr@sha256:…` keeps its digest.
 */
data class ImageReference(
    val registry: String,
    val repository: String,
    val tag: String,
    val digest: String?
) {
    val apiHost: String
        get() = if (registry == DOCKER_HUB_REGISTRY) DOCKER_HUB_API_HOST else registry

    val isPinnedToDigest: Boolean
        get() = digest != null

    override fun toString(): String {
        val name = if (registry == DOCKER_HUB_REGISTRY) repository.removePrefix("$OFFICIAL_NAMESPACE/") else "$registry/$repository"
        return if (digest != null) "$name@$digest" else "$name:$tag"
    }

    companion object {
        const val DOCKER_HUB_REGISTRY = "docker.io"
        private const val DOCKER_HUB_API_HOST = "registry-1.docker.io"
        private const val OFFICIAL_NAMESPACE = "library"
        private const val DEFAULT_TAG = "latest"
        private const val LOCAL_REGISTRY = "localhost"

        fun parse(image: String): ImageReference? {
            val trimmed = image.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("sha256:")) {
                return null
            }

            val digestSeparator = trimmed.indexOf('@')
            val digest = if (digestSeparator >= 0) {
                trimmed.substring(digestSeparator + 1).takeIf { it.isNotEmpty() }
            } else {
                null
            }
            val name = if (digestSeparator >= 0) trimmed.substring(0, digestSeparator) else trimmed

            val firstSlash = name.indexOf('/')
            val prefix = if (firstSlash > 0) name.substring(0, firstSlash) else null
            val hasRegistry = prefix != null &&
                    (prefix.contains('.') || prefix.contains(':') || prefix == LOCAL_REGISTRY)
            val registry = if (hasRegistry) requireNotNull(prefix) else DOCKER_HUB_REGISTRY
            val remainder = if (hasRegistry) name.substring(firstSlash + 1) else name

            val tagSeparator = remainder.lastIndexOf(':')
            val hasTag = tagSeparator > 0 && !remainder.substring(tagSeparator + 1).contains('/')
            val tag = if (hasTag) remainder.substring(tagSeparator + 1) else DEFAULT_TAG
            val path = if (hasTag) remainder.substring(0, tagSeparator) else remainder
            if (path.isEmpty() || tag.isEmpty()) {
                return null
            }

            val repository = if (registry == DOCKER_HUB_REGISTRY && !path.contains('/')) {
                "$OFFICIAL_NAMESPACE/$path"
            } else {
                path
            }
            return ImageReference(registry, repository, tag, digest)
        }
    }
}
