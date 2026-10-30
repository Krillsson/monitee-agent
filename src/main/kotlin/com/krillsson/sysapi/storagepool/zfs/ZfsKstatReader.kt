package com.krillsson.sysapi.storagepool.zfs

import java.io.File

data class ZfsKstatPool(
    val name: String,
    val state: String
)

class ZfsKstatReader(private val kstatRoot: File = File("/proc/spl/kstat/zfs")) {

    fun exists(): Boolean = kstatRoot.isDirectory

    fun readPools(): List<ZfsKstatPool> {
        val poolDirs = kstatRoot.listFiles { file -> file.isDirectory }.orEmpty()
        return poolDirs.mapNotNull { poolDir ->
            File(poolDir, "state").takeIf { it.isFile }
                ?.readText()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { ZfsKstatPool(poolDir.name, it) }
        }.sortedBy { it.name }
    }
}
