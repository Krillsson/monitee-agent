package com.krillsson.sysapi.workload.icon

object ImageNames {

    private val ALIASES = mapOf("pms" to "plex")
    private val GENERIC_WORDS = setOf("docker", "server", "web", "app", "arch", "linux", "alpine", "official")
    private const val MIN_WORD_LENGTH = 3

    fun dockerCandidates(image: String): List<String> {
        val path = repositoryPath(image)
        return when {
            path.isEmpty() -> emptyList()
            else -> withAliases(
                listOf(path.last(), path.joinToString("-")) +
                    wordRuns(path.last()) +
                    path.dropLast(1).reversed().filter { it !in GENERIC_WORDS }
            )
        }
    }

    fun nameCandidates(name: String): List<String> {
        val lowercase = name.trim().lowercase()
        return withAliases(listOf(lowercase) + wordRuns(lowercase)).filter { it.isNotEmpty() }
    }

    fun osTypeCandidates(osType: String?): List<String> = when (osType) {
        null -> emptyList()
        else -> listOf(osType.lowercase(), "${osType.lowercase()}-linux")
    }

    private fun withAliases(candidates: List<String>) = candidates.map { ALIASES[it] ?: it }.distinct()

    private fun wordRuns(name: String): List<String> {
        val words = name.split('-', '_', '.').filter { it.isNotEmpty() }
        return (words.size - 1 downTo 1).flatMap { length ->
            (words.size - length downTo 0).map { start -> words.subList(start, start + length).joinToString("-") }
        }.filter { it.length >= MIN_WORD_LENGTH && it !in GENERIC_WORDS }
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
