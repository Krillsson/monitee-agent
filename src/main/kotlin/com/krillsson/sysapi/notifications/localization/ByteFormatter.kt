package com.krillsson.sysapi.notifications.localization

import org.springframework.stereotype.Component

@Component
class ByteFormatter {
    private val formatSiBytes: Boolean = false
    private val formatNetworkAsBytesPerSecond: Boolean = false

    fun format(bytes: Long): String {
        return humanReadableByteCount(bytes, formatSiBytes)
    }

    fun formatNetworkRate(bytesPerSecond: Long): String {
        val value = if (formatNetworkAsBytesPerSecond) bytesPerSecond else bytesPerSecond * 8
        val unit = if (formatNetworkAsBytesPerSecond) "B" else "b"
        return humanReadableByteCount(value, (!formatNetworkAsBytesPerSecond) || formatSiBytes, unit) + "/s"
    }

    private fun humanReadableByteCount(
        bytes: Long,
        si: Boolean = false,
        unitCharacter: String = "B"
    ): String {
        val unit = if (si) 1000 else 1024
        if (bytes < unit) return "$bytes $unitCharacter"
        val exp = (Math.log(bytes.toDouble()) / Math.log(unit.toDouble())).toInt()
        val pre = (if (si) "kMGTPE" else "KMGTPE")[exp - 1].toString() + if (si) "" else ""
        return String.format(
            "%.1f %s$unitCharacter",
            bytes / Math.pow(unit.toDouble(), exp.toDouble()),
            pre
        )
    }
}