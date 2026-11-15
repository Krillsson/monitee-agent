package com.krillsson.sysapi.workload.icon

class DashboardIconIndex(private val formatsByName: Map<String, Set<String>>) {

    private val nameBySimplified: Map<String, String> =
        formatsByName.keys.sortedByDescending { it.length }.associateBy { simplify(it) }

    fun find(candidate: String): String? {
        val name = candidate.lowercase()
        return when {
            name in formatsByName -> name
            else -> nameBySimplified[simplify(name)]
        }
    }

    fun formats(name: String): Set<String> = formatsByName[name].orEmpty()

    private fun simplify(name: String) = name.filter { it.isLetterOrDigit() }

    companion object {
        fun parse(lines: List<String>): DashboardIconIndex = DashboardIconIndex(
            lines.filter { it.isNotBlank() }
                .associate { line ->
                    val (name, formats) = line.split('\t', limit = 2)
                    name to formats.split(',').toSet()
                }
        )
    }
}
