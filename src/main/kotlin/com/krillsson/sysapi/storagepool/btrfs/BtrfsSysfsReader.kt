package com.krillsson.sysapi.storagepool.btrfs

import java.io.File

data class BtrfsErrorStats(
    val writeErrors: Long,
    val readErrors: Long,
    val flushErrors: Long,
    val corruptionErrors: Long,
    val generationErrors: Long
)

data class BtrfsDevice(
    val devid: Long,
    val missing: Boolean,
    val replaceTarget: Boolean,
    val errorStats: BtrfsErrorStats?
)

data class BtrfsBlockDevice(
    val kname: String,
    val sizeBytes: Long?
)

data class BtrfsFilesystem(
    val fsid: String,
    val label: String?,
    val devices: List<BtrfsDevice>,
    val blockDevices: List<BtrfsBlockDevice>,
    val profilesByAllocationType: Map<String, Set<String>>,
    val allocatedBytes: Long?
)

class BtrfsSysfsReader(private val btrfsRoot: File = File("/sys/fs/btrfs")) {

    companion object {
        private const val SECTOR_SIZE_BYTES = 512L
        private val fsidRegex = Regex("""^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$""")

        private val errorCounterNames = setOf("write_errs", "read_errs", "flush_errs", "corruption_errs", "generation_errs")

        fun parseErrorStats(raw: String): BtrfsErrorStats? {
            val counters = raw.lines().mapNotNull(::parseErrorCounter).toMap()
            return when {
                counters.keys.containsAll(errorCounterNames) -> BtrfsErrorStats(
                    writeErrors = counters.getValue("write_errs"),
                    readErrors = counters.getValue("read_errs"),
                    flushErrors = counters.getValue("flush_errs"),
                    corruptionErrors = counters.getValue("corruption_errs"),
                    generationErrors = counters.getValue("generation_errs")
                )

                else -> null
            }
        }

        private fun parseErrorCounter(line: String): Pair<String, Long>? {
            val parts = line.trim().split(Regex("\\s+"))
            val count = parts.getOrNull(1)?.toLongOrNull()
            return when {
                parts.size != 2 || count == null -> null
                else -> parts[0] to count
            }
        }
    }

    fun exists(): Boolean = btrfsRoot.isDirectory

    fun readFilesystems(): List<BtrfsFilesystem> {
        val fsDirs = btrfsRoot.listFiles { file -> file.isDirectory && fsidRegex.matches(file.name) }.orEmpty()
        return fsDirs.map(::readFilesystem).sortedBy { it.fsid }
    }

    private fun readFilesystem(fsDir: File): BtrfsFilesystem {
        val allocationTypeDirs = File(fsDir, "allocation").listFiles { file -> file.isDirectory } ?: emptyArray()
        val diskUsed = allocationTypeDirs.mapNotNull { it.readAttribute("disk_used")?.toLongOrNull() }
        return BtrfsFilesystem(
            fsid = fsDir.name,
            label = fsDir.readAttribute("label"),
            devices = (File(fsDir, "devinfo").listFiles { file -> file.isDirectory } ?: emptyArray())
                .mapNotNull(::readDevice)
                .sortedBy { it.devid },
            blockDevices = (File(fsDir, "devices").listFiles() ?: emptyArray())
                .map { BtrfsBlockDevice(it.name, it.readAttribute("size")?.toLongOrNull()?.times(SECTOR_SIZE_BYTES)) }
                .sortedBy { it.kname },
            profilesByAllocationType = allocationTypeDirs.associate { typeDir ->
                typeDir.name to (typeDir.listFiles { file -> file.isDirectory } ?: emptyArray()).map { it.name }.toSet()
            },
            allocatedBytes = diskUsed.takeIf { it.isNotEmpty() }?.sum()
        )
    }

    private fun readDevice(devDir: File): BtrfsDevice? {
        return when (val devid = devDir.name.toLongOrNull()) {
            null -> null
            else -> BtrfsDevice(
                devid = devid,
                missing = devDir.readAttribute("missing") == "1",
                replaceTarget = devDir.readAttribute("replace_target") == "1",
                errorStats = devDir.readAttribute("error_stats")?.let(::parseErrorStats)
            )
        }
    }

    private fun File.readAttribute(name: String): String? =
        File(this, name).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
}
