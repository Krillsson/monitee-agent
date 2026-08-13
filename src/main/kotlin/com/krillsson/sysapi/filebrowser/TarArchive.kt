package com.krillsson.sysapi.filebrowser

import java.io.InputStream
import java.nio.charset.StandardCharsets

data class TarEntry(val name: String, val sizeBytes: Long, val directory: Boolean)

/**
 * A reader for the part of the tar format the archives on a NAS actually use: ustar and the two
 * ways a name longer than 100 characters is carried, GNU's long name record and pax's extended
 * header. Link and device records are recognised so they can be skipped rather than mistaken for
 * files.
 *
 * Written by hand because the JDK has no tar, and commons-compress is a dependency the agent does
 * not otherwise need.
 */
class TarArchive(private val input: InputStream) {

    companion object {
        private const val BLOCK = 512
        private const val NAME = 0
        private const val NAME_LENGTH = 100
        private const val SIZE = 124
        private const val SIZE_LENGTH = 12
        private const val TYPE_FLAG = 156
        private const val PREFIX = 345
        private const val PREFIX_LENGTH = 155
        private const val MAX_HEADER_RECORD = BLOCK * 32L
        private const val PATH_RECORD = "path="

        private val REGULAR_FILE = setOf('0'.code, 0, '7'.code)
        private const val DIRECTORY = '5'.code
        private const val LONG_NAME = 'L'.code
        private const val PAX_HEADER = 'x'.code
        private const val PAX_GLOBAL_HEADER = 'X'.code
    }

    private val header = ByteArray(BLOCK)

    fun forEach(action: (TarEntry, InputStream) -> Unit) {
        var longName: String? = null
        while (true) {
            if (!readFully(header) || header.all { it.toInt() == 0 }) {
                return
            }
            val size = octal(SIZE, SIZE_LENGTH)
            val type = header[TYPE_FLAG].toInt()
            val name = longName ?: nameOf()
            longName = null
            when (type) {
                LONG_NAME -> longName = readRecord(size).takeWhile { !it.isISOControl() }
                PAX_HEADER, PAX_GLOBAL_HEADER -> longName = pathOf(readRecord(size))
                DIRECTORY -> {
                    action(TarEntry(name, 0, true), InputStream.nullInputStream())
                    skip(size + padding(size))
                }

                in REGULAR_FILE -> {
                    val data = EntryStream(size)
                    action(TarEntry(name, size, false), data)
                    data.drain()
                    skipPadding(size)
                }

                else -> skip(size + padding(size))
            }
        }
    }

    private fun nameOf(): String {
        val name = string(NAME, NAME_LENGTH)
        val prefix = string(PREFIX, PREFIX_LENGTH)
        return if (prefix.isEmpty()) name else "$prefix/$name"
    }

    private fun pathOf(records: String): String? = records.lineSequence()
        .map { it.substringAfter(' ', "") }
        .filter { it.startsWith(PATH_RECORD) }
        .map { it.removePrefix(PATH_RECORD) }
        .lastOrNull()

    private fun string(offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it].toInt() == 0 } ?: (offset + length)
        return String(header, offset, end - offset, StandardCharsets.UTF_8)
    }

    private fun octal(offset: Int, length: Int): Long {
        val text = string(offset, length).trim()
        return text.takeWhile { it in '0'..'7' }.toLongOrNull(8) ?: 0
    }

    private fun padding(size: Long) = (BLOCK - size % BLOCK) % BLOCK

    private fun readRecord(size: Long): String {
        if (size < 0 || size > MAX_HEADER_RECORD) {
            throw FileBrowserException("The archive holds a header record that is too long to be one")
        }
        val bytes = ByteArray(size.toInt())
        if (!readFully(bytes)) {
            throw FileBrowserException("The archive ends in the middle of a header record")
        }
        skipPadding(size)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun skipPadding(size: Long) = skip(padding(size))

    private fun skip(count: Long) {
        var left = count
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() < 0) {
                    return
                }
                left--
            } else {
                left -= skipped
            }
        }
    }

    private fun readFully(into: ByteArray): Boolean {
        var read = 0
        while (read < into.size) {
            val count = input.read(into, read, into.size - read)
            if (count < 0) {
                return false
            }
            read += count
        }
        return true
    }

    private inner class EntryStream(private var left: Long) : InputStream() {

        override fun read(): Int {
            if (left <= 0) {
                return -1
            }
            val value = input.read()
            if (value >= 0) {
                left--
            }
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (left <= 0) {
                return -1
            }
            val count = input.read(buffer, offset, minOf(length.toLong(), left).toInt())
            if (count > 0) {
                left -= count
            }
            return count
        }

        fun drain() {
            skip(left)
            left = 0
        }
    }
}
