package com.krillsson.sysapi.storagepool.md

import java.io.File

data class MdSysfsDevice(
    val kname: String,
    val state: Set<String>,
    val errors: Long?
)

data class MdSysfsArray(
    val device: String,
    val arrayState: String?,
    val degraded: Int?,
    val level: String?,
    val raidDisks: Int?,
    val chunkSizeBytes: Long?,
    val metadataVersion: String?,
    val uuid: String?,
    val syncAction: String?,
    val syncCompletedSectors: Pair<Long, Long>?,
    val syncSpeedKbPerSec: Long?,
    val mismatchCnt: Long?,
    val devices: List<MdSysfsDevice>
)

class MdSysfsReader(private val sysRoot: File = File("/sys")) {

    fun readArrays(): List<MdSysfsArray> {
        val blockDir = File(sysRoot, "block")
        val arrayDirs = blockDir.listFiles { file -> file.isDirectory && file.name.startsWith("md") } ?: return emptyList()
        return arrayDirs.mapNotNull { readArray(it) }.sortedBy { it.device }
    }

    private fun readArray(deviceDir: File): MdSysfsArray? {
        val mdDir = File(deviceDir, "md")
        if (!mdDir.isDirectory) return null
        return MdSysfsArray(
            device = deviceDir.name,
            arrayState = mdDir.readAttribute("array_state"),
            degraded = mdDir.readAttribute("degraded")?.toIntOrNull(),
            level = mdDir.readAttribute("level"),
            raidDisks = mdDir.readAttribute("raid_disks")?.toIntOrNull(),
            chunkSizeBytes = mdDir.readAttribute("chunk_size")?.toLongOrNull(),
            metadataVersion = mdDir.readAttribute("metadata_version"),
            uuid = mdDir.readAttribute("uuid"),
            syncAction = mdDir.readAttribute("sync_action"),
            syncCompletedSectors = mdDir.readAttribute("sync_completed")?.let(::parseSyncCompleted),
            syncSpeedKbPerSec = mdDir.readAttribute("sync_speed")?.toLongOrNull(),
            mismatchCnt = mdDir.readAttribute("mismatch_cnt")?.toLongOrNull(),
            devices = (mdDir.listFiles { file -> file.isDirectory && file.name.startsWith("dev-") } ?: emptyArray())
                .map(::readDevice)
                .sortedBy { it.kname }
        )
    }

    private fun readDevice(devDir: File): MdSysfsDevice {
        val state = devDir.readAttribute("state")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        return MdSysfsDevice(
            kname = devDir.name.removePrefix("dev-"),
            state = state,
            errors = devDir.readAttribute("errors")?.toLongOrNull()
        )
    }

    private fun parseSyncCompleted(raw: String): Pair<Long, Long>? {
        val parts = raw.split("/")
        if (parts.size != 2) return null
        val done = parts[0].trim().toLongOrNull() ?: return null
        val total = parts[1].trim().toLongOrNull() ?: return null
        return done to total
    }

    private fun File.readAttribute(name: String): String? =
        File(this, name).takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
}
