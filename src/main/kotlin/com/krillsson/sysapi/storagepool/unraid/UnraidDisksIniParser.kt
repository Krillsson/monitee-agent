package com.krillsson.sysapi.storagepool.unraid

data class UnraidDiskInfo(
    val slotName: String,
    val device: String?,
    val id: String?,
    val status: String?,
    val numErrors: Long?,
    val temperatureCelsius: Int?
)

object UnraidDisksIniParser {

    private val sectionRegex = Regex("""^\[\"?(.+?)\"?]$""")

    fun parse(text: String): List<UnraidDiskInfo> {
        val sections = mutableListOf<Pair<String, MutableMap<String, String>>>()
        var current: MutableMap<String, String>? = null
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue
            val section = sectionRegex.matchEntire(line)
            if (section != null) {
                current = mutableMapOf()
                sections += section.groupValues[1] to current
                continue
            }
            val idx = line.indexOf('=')
            if (idx <= 0 || current == null) continue
            val key = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim().trim('"')
            current[key] = value
        }
        return sections.map { (slotName, values) ->
            UnraidDiskInfo(
                slotName = slotName,
                device = values["device"],
                id = values["id"],
                status = values["status"],
                numErrors = values["numErrors"]?.toLongOrNull(),
                temperatureCelsius = values["temp"]?.toIntOrNull()
            )
        }
    }
}
