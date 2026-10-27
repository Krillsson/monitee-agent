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

        fun parseErrorStats(raw: String): BtrfsErrorStats? {
            val values = raw.lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size != 2) null else parts[0] to parts[1].toLongOrNull()
            }.toMap()
            return BtrfsErrorStats(
                writeErrors = values["write_errs"] ?: return null,
                readErrors = values["read_errs"] ?: return null,
                flushErrors = values["flush_errs"] ?: return null,
                corruptionErrors = values["corruption_errs"] ?: return null,
                generationErrors = values["generation_errs"] ?: return null
            )
        }
    }

    fun exists(): Boolean = btrfsRoot.isDirectory

    fun readFilesystems(): List<BtrfsFilesystem> {
        val fsDirs = btrfsRoot.listFiles { file -> file.isDirectory && fsidRegex.matches(file.name) } ?: return emptyList()
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
        val devid = devDir.name.toLongOrNull() ?: return null
        return BtrfsDevice(
            devid = devid,
            missing = devDir.readAttribute("missing") == "1",
            replaceTarget = devDir.readAttribute("replace_target") == "1",
            errorStats = devDir.readAttribute("error_stats")?.let(::parseErrorStats)
        )
    }

    private fun File.readAttribute(name: String): String? =
        File(this, name).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
}
