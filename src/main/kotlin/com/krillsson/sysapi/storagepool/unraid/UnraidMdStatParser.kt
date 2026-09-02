package com.krillsson.sysapi.storagepool.unraid

data class UnraidDisk(
    val slot: Int,
    val status: String?,
    val numErrors: Long?
)

data class UnraidArray(
    val mdState: String?,
    val numDisks: Int?,
    val resyncAction: String?,
    val resyncSizeK: Long?,
    val resyncPosK: Long?,
    val resyncTotalK: Long?,
    val syncStartedAtEpochSeconds: Long?,
    val syncCompletedAtEpochSeconds: Long?,
    val syncErrors: Long?,
    val syncExitCode: Int?,
    val disks: List<UnraidDisk>
)

object UnraidMdStatParser {

    private val slotRegex = Regex("""^rdevStatus\.(\d+)$""")

    fun parse(text: String): UnraidArray? {
        val values = text.lines().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toMap()

        if (values["mdState"] == null) return null

        val slots = values.keys.mapNotNull { key -> slotRegex.matchEntire(key)?.groupValues?.get(1)?.toIntOrNull() }
            .toSortedSet()

        val disks = slots.map { slot ->
            UnraidDisk(
                slot = slot,
                status = values["rdevStatus.$slot"],
                numErrors = values["rdevNumErrors.$slot"]?.toLongOrNull()
            )
        }

        return UnraidArray(
            mdState = values["mdState"],
            numDisks = values["mdNumDisks"]?.toIntOrNull(),
            resyncAction = values["mdResyncAction"],
            resyncSizeK = values["mdResyncSize"]?.toLongOrNull(),
            resyncPosK = values["mdResyncPos"]?.toLongOrNull(),
            resyncTotalK = values["mdResync"]?.toLongOrNull(),
            syncStartedAtEpochSeconds = values["sbSynced"]?.toLongOrNull(),
            syncCompletedAtEpochSeconds = values["sbSynced2"]?.toLongOrNull(),
            syncErrors = values["sbSyncErrs"]?.toLongOrNull(),
            syncExitCode = values["sbSyncExit"]?.toIntOrNull(),
            disks = disks
        )
    }
}
