package com.krillsson.sysapi.workload.icon

object ImageNames {

    fun dockerCandidates(image: String): List<String> {
        val path = repositoryPath(image)
        return when {
            path.isEmpty() -> emptyList()
            else -> listOf(path.last(), path.joinToString("-")).distinct()
        }
    }

    private fun repositoryPath(image: String): List<String> {
        val withoutDigest = image.trim().lowercase().substringBefore('@')
        val lastSlash = withoutDigest.lastIndexOf('/')
        val withoutTag = withoutDigest.substring(0, lastSlash + 1) + withoutDigest.substring(lastSlash + 1).substringBefore(':')
        val segments = withoutTag.split('/').filter { it.isNotBlank() }
        val withoutRegistry = when {
            segments.size > 1 && isRegistry(segments.first()) -> segments.drop(1)
            else -> segments
        }
        return when {
            image.trim().startsWith("sha256:") -> emptyList()
            withoutRegistry.size > 1 && withoutRegistry.first() == "library" -> withoutRegistry.drop(1)
            else -> withoutRegistry
        }
    }

    private fun isRegistry(segment: String) = '.' in segment || ':' in segment || segment == "localhost"
}
