package com.krillsson.sysapi.storagepool.md

data class MdStatMember(
    val name: String,
    val role: Int?,
    val flags: Set<Char>
)

data class MdStatResync(
    val action: String,
    val percentComplete: Float,
    val finishEtaMinutes: Float?,
    val speedKbPerSec: Long?
)

data class MdStatArray(
    val device: String,
    val activityState: String,
    val level: String?,
    val members: List<MdStatMember>,
    val totalBlocks: Long?,
    val bitmap: String?,
    val resync: MdStatResync?
)

object MdStatParser {

    private val headerRegex = Regex("""^(\S+)\s*:\s*(\S+)\s*(.*)$""")
    private val memberRegex = Regex("""^([\w.:-]+)\[(\d+)](?:\(([A-Z]+)\))?$""")
    private val blocksRegex = Regex("""^(\d+)\s+blocks""")
    private val bitmapRegex = Regex("""\[[U_]+]""")
    private val resyncRegex =
        Regex("""(resync|recovery|check|reshape)\s*=\s*([\d.]+)%(?:.*?finish=([\d.]+)min)?(?:.*?speed=(\d+)K/sec)?""")

    fun parse(text: String): List<MdStatArray> {
        val lines = text.lines()
        val arrays = mutableListOf<MdStatArray>()
        var i = 0
        while (i < lines.size) {
            val header = headerRegex.matchEntire(lines[i].trimEnd())
            if (header == null || !header.groupValues[1].startsWith("md")) {
                i++
                continue
            }
            val (device, activityState, rest) = header.destructured
            val members = mutableListOf<MdStatMember>()
            var level: String? = null
            for (token in rest.trim().split(Regex("\\s+")).filter { it.isNotBlank() }) {
                val member = memberRegex.matchEntire(token)
                when {
                    member != null -> members += MdStatMember(
                        name = member.groupValues[1],
                        role = member.groupValues[2].toIntOrNull(),
                        flags = member.groupValues[3].toSet()
                    )

                    token.startsWith("(") && token.endsWith(")") -> Unit
                    level == null -> level = token
                }
            }

            var totalBlocks: Long? = null
            var bitmap: String? = null
            var resync: MdStatResync? = null
            var j = i + 1
            while (j < lines.size && lines[j].startsWith(" ")) {
                val detail = lines[j].trim()
                blocksRegex.find(detail)?.let { totalBlocks = it.groupValues[1].toLongOrNull() }
                bitmapRegex.find(detail)?.let { bitmap = it.value }
                resyncRegex.find(detail)?.let {
                    resync = MdStatResync(
                        action = it.groupValues[1],
                        percentComplete = it.groupValues[2].toFloat(),
                        finishEtaMinutes = it.groupValues[3].toFloatOrNull(),
                        speedKbPerSec = it.groupValues[4].toLongOrNull()
                    )
                }
                j++
            }

            arrays += MdStatArray(device, activityState, level, members, totalBlocks, bitmap, resync)
            i = j
        }
        return arrays
    }
}
