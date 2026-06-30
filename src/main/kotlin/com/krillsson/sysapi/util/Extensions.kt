package com.krillsson.sysapi.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.common.primitives.Longs
import com.krillsson.sysapi.core.domain.processes.ProcessSort
import com.krillsson.sysapi.core.domain.system.OperatingSystem
import com.krillsson.sysapi.core.domain.system.Platform
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import oshi.software.os.OSProcess
import oshi.util.PlatformEnum
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

fun oshi.software.os.OperatingSystem.asOperatingSystem(): OperatingSystem {
    return OperatingSystem(
        manufacturer,
        family,
        OperatingSystem.VersionInfo(
            versionInfo.version,
            versionInfo.codeName.orEmpty(),
            versionInfo.buildNumber
        )
    )
}

fun File.lineCount(): Long {
    var lines = 0L
    val fis = FileInputStream(this)
    val buffer = ByteArray(
        8 * 1024
    )
    var read: Int
    while (fis.read(buffer).also { read = it } != -1) {
        for (i in 0 until read) {
            if (buffer[i] == '\n'.code.toByte()) lines++
        }
    }
    fis.close()
    return lines
}

fun Instant.toOffsetDateTime() = atZone(ZoneId.systemDefault()).toOffsetDateTime()

fun Instant.encodeAsCursor(): String {
    return Base64.getEncoder().encodeToString(Longs.toByteArray(toEpochMilli()))
}

fun String.decodeAsInstantCursor(): Instant {
    return Instant.ofEpochMilli(Longs.fromByteArray(Base64.getDecoder().decode(this)))
}

fun PlatformEnum.asPlatform(): Platform {
    return Platform.values().first { this.name == it.name }
}

fun ProcessSort.asOshiProcessSort(): Comparator<OSProcess> {
    return when (this) {
        ProcessSort.CPU -> oshi.software.os.OperatingSystem.ProcessSorting.CPU_DESC
        ProcessSort.MEMORY -> oshi.software.os.OperatingSystem.ProcessSorting.RSS_DESC
        ProcessSort.OLDEST -> oshi.software.os.OperatingSystem.ProcessSorting.UPTIME_DESC
        ProcessSort.NEWEST -> oshi.software.os.OperatingSystem.ProcessSorting.UPTIME_ASC
        ProcessSort.PID -> oshi.software.os.OperatingSystem.ProcessSorting.PID_ASC
        ProcessSort.PARENTPID -> oshi.software.os.OperatingSystem.ProcessSorting.PARENTPID_ASC
        ProcessSort.NAME -> oshi.software.os.OperatingSystem.ProcessSorting.NAME_ASC
    }
}

fun OffsetDateTime.asString() = format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun <R : Any> R.logger(): Lazy<Logger> {
    return lazy { LoggerFactory.getLogger(this::class.java.name.removeSuffix("\$Companion")) }
}

fun Any.reflectionToString(): String {
    return ObjectMapper()
        .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        .writerWithDefaultPrettyPrinter().writeValueAsString(this)
}

fun Int.asHex() = Integer.toHexString(this).uppercase()
fun Long?.orDefault(): Long {
    return this ?: -1
}

fun Float?.orDefault(): Float {
    return this ?: -1f
}

fun Double?.orDefault(): Double {
    return this ?: -1.0
}