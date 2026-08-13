package com.krillsson.sysapi.filebrowser

import org.springframework.stereotype.Service

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    ARCHIVE,
    DOCUMENT,
    TEXT,
    APPLICATION,
    OTHER
}

data class FileType(val mimeType: String, val category: FileCategory)

enum class ArchiveFormat {
    ZIP,
    TAR,
    TAR_GZ,
    GZIP
}

@Service
class FileTypeRegistry {

    companion object {
        const val FOLDER_ICON = "folder"
        const val UNKNOWN_ICON = "blank"
        private const val OCTET_STREAM = "application/octet-stream"

        private val LOG_EXTENSIONS = setOf("log", "txt", "out", "")

        private val THUMBNAIL_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")

        private val ARCHIVE_SUFFIXES = listOf(
            ".tar.gz" to ArchiveFormat.TAR_GZ,
            ".tgz" to ArchiveFormat.TAR_GZ,
            ".tar" to ArchiveFormat.TAR,
            ".gz" to ArchiveFormat.GZIP,
            ".zip" to ArchiveFormat.ZIP
        )

        private val CATEGORY_ICONS = mapOf(
            FileCategory.IMAGE to "image",
            FileCategory.VIDEO to "mov",
            FileCategory.AUDIO to "mp3",
            FileCategory.ARCHIVE to "zip",
            FileCategory.DOCUMENT to UNKNOWN_ICON,
            FileCategory.TEXT to "txt",
            FileCategory.APPLICATION to UNKNOWN_ICON,
            FileCategory.OTHER to UNKNOWN_ICON
        )

        private val TYPES = mapOf(
        "jpg" to FileType("image/jpeg", FileCategory.IMAGE),
        "jpeg" to FileType("image/jpeg", FileCategory.IMAGE),
        "png" to FileType("image/png", FileCategory.IMAGE),
        "gif" to FileType("image/gif", FileCategory.IMAGE),
        "bmp" to FileType("image/bmp", FileCategory.IMAGE),
        "webp" to FileType("image/webp", FileCategory.IMAGE),
        "svg" to FileType("image/svg+xml", FileCategory.IMAGE),
        "tif" to FileType("image/tiff", FileCategory.IMAGE),
        "tiff" to FileType("image/tiff", FileCategory.IMAGE),
        "heic" to FileType("image/heic", FileCategory.IMAGE),
        "ico" to FileType("image/vnd.microsoft.icon", FileCategory.IMAGE),
        "psd" to FileType("image/vnd.adobe.photoshop", FileCategory.IMAGE),
        "cr2" to FileType("image/x-canon-cr2", FileCategory.IMAGE),
        "nef" to FileType("image/x-nikon-nef", FileCategory.IMAGE),
        "dng" to FileType("image/x-adobe-dng", FileCategory.IMAGE),
        "raw" to FileType("image/x-dcraw", FileCategory.IMAGE),
        "mp4" to FileType("video/mp4", FileCategory.VIDEO),
        "mkv" to FileType("video/x-matroska", FileCategory.VIDEO),
        "avi" to FileType("video/x-msvideo", FileCategory.VIDEO),
        "mov" to FileType("video/quicktime", FileCategory.VIDEO),
        "wmv" to FileType("video/x-ms-wmv", FileCategory.VIDEO),
        "flv" to FileType("video/x-flv", FileCategory.VIDEO),
        "webm" to FileType("video/webm", FileCategory.VIDEO),
        "m4v" to FileType("video/x-m4v", FileCategory.VIDEO),
        "mpg" to FileType("video/mpeg", FileCategory.VIDEO),
        "mpeg" to FileType("video/mpeg", FileCategory.VIDEO),
        "3gp" to FileType("video/3gpp", FileCategory.VIDEO),
        "vob" to FileType("video/mpeg", FileCategory.VIDEO),
        "ogv" to FileType("video/ogg", FileCategory.VIDEO),
        "mp3" to FileType("audio/mpeg", FileCategory.AUDIO),
        "flac" to FileType("audio/flac", FileCategory.AUDIO),
        "wav" to FileType("audio/wav", FileCategory.AUDIO),
        "aac" to FileType("audio/aac", FileCategory.AUDIO),
        "ogg" to FileType("audio/ogg", FileCategory.AUDIO),
        "m4a" to FileType("audio/mp4", FileCategory.AUDIO),
        "wma" to FileType("audio/x-ms-wma", FileCategory.AUDIO),
        "aiff" to FileType("audio/aiff", FileCategory.AUDIO),
        "mid" to FileType("audio/midi", FileCategory.AUDIO),
        "m3u" to FileType("audio/x-mpegurl", FileCategory.AUDIO),
        "zip" to FileType("application/zip", FileCategory.ARCHIVE),
        "rar" to FileType("application/vnd.rar", FileCategory.ARCHIVE),
        "7z" to FileType("application/x-7z-compressed", FileCategory.ARCHIVE),
        "tar" to FileType("application/x-tar", FileCategory.ARCHIVE),
        "gz" to FileType("application/gzip", FileCategory.ARCHIVE),
        "tgz" to FileType("application/gzip", FileCategory.ARCHIVE),
        "bz2" to FileType("application/x-bzip2", FileCategory.ARCHIVE),
        "xz" to FileType("application/x-xz", FileCategory.ARCHIVE),
        "iso" to FileType("application/x-iso9660-image", FileCategory.ARCHIVE),
        "dmg" to FileType("application/x-apple-diskimage", FileCategory.ARCHIVE),
        "cab" to FileType("application/vnd.ms-cab-compressed", FileCategory.ARCHIVE),
        "pdf" to FileType("application/pdf", FileCategory.DOCUMENT),
        "doc" to FileType("application/msword", FileCategory.DOCUMENT),
        "docx" to FileType("application/vnd.openxmlformats-officedocument.wordprocessingml.document", FileCategory.DOCUMENT),
        "xls" to FileType("application/vnd.ms-excel", FileCategory.DOCUMENT),
        "xlsx" to FileType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", FileCategory.DOCUMENT),
        "ppt" to FileType("application/vnd.ms-powerpoint", FileCategory.DOCUMENT),
        "pptx" to FileType("application/vnd.openxmlformats-officedocument.presentationml.presentation", FileCategory.DOCUMENT),
        "odt" to FileType("application/vnd.oasis.opendocument.text", FileCategory.DOCUMENT),
        "ods" to FileType("application/vnd.oasis.opendocument.spreadsheet", FileCategory.DOCUMENT),
        "rtf" to FileType("application/rtf", FileCategory.DOCUMENT),
        "epub" to FileType("application/epub+zip", FileCategory.DOCUMENT),
        "mobi" to FileType("application/x-mobipocket-ebook", FileCategory.DOCUMENT),
        "txt" to FileType("text/plain", FileCategory.TEXT),
        "md" to FileType("text/markdown", FileCategory.TEXT),
        "csv" to FileType("text/csv", FileCategory.TEXT),
        "log" to FileType("text/plain", FileCategory.TEXT),
        "nfo" to FileType("text/plain", FileCategory.TEXT),
        "ini" to FileType("text/plain", FileCategory.TEXT),
        "conf" to FileType("text/plain", FileCategory.TEXT),
        "cfg" to FileType("text/plain", FileCategory.TEXT),
        "json" to FileType("application/json", FileCategory.TEXT),
        "xml" to FileType("application/xml", FileCategory.TEXT),
        "yaml" to FileType("application/yaml", FileCategory.TEXT),
        "yml" to FileType("application/yaml", FileCategory.TEXT),
        "sql" to FileType("application/sql", FileCategory.TEXT),
        "sh" to FileType("application/x-sh", FileCategory.TEXT),
        "bash" to FileType("application/x-sh", FileCategory.TEXT),
        "py" to FileType("text/x-python", FileCategory.TEXT),
        "js" to FileType("text/javascript", FileCategory.TEXT),
        "ts" to FileType("text/typescript", FileCategory.TEXT),
        "html" to FileType("text/html", FileCategory.TEXT),
        "htm" to FileType("text/html", FileCategory.TEXT),
        "css" to FileType("text/css", FileCategory.TEXT),
        "java" to FileType("text/x-java-source", FileCategory.TEXT),
        "kt" to FileType("text/x-kotlin", FileCategory.TEXT),
        "kts" to FileType("text/x-kotlin", FileCategory.TEXT),
        "go" to FileType("text/x-go", FileCategory.TEXT),
        "rb" to FileType("text/x-ruby", FileCategory.TEXT),
        "php" to FileType("application/x-httpd-php", FileCategory.TEXT),
        "c" to FileType("text/x-c", FileCategory.TEXT),
        "cpp" to FileType("text/x-c", FileCategory.TEXT),
        "h" to FileType("text/x-c", FileCategory.TEXT),
        "cs" to FileType("text/x-csharp", FileCategory.TEXT),
        "swift" to FileType("text/x-swift", FileCategory.TEXT),
        "gradle" to FileType("text/x-groovy", FileCategory.TEXT),
        "exe" to FileType("application/vnd.microsoft.portable-executable", FileCategory.APPLICATION),
        "msi" to FileType("application/x-msi", FileCategory.APPLICATION),
        "deb" to FileType("application/vnd.debian.binary-package", FileCategory.APPLICATION),
        "rpm" to FileType("application/x-rpm", FileCategory.APPLICATION),
        "apk" to FileType("application/vnd.android.package-archive", FileCategory.APPLICATION),
        "jar" to FileType("application/java-archive", FileCategory.APPLICATION),
        "bin" to FileType("application/octet-stream", FileCategory.APPLICATION),
        "dll" to FileType("application/octet-stream", FileCategory.APPLICATION),
        "img" to FileType("application/octet-stream", FileCategory.APPLICATION),
        "db" to FileType("application/octet-stream", FileCategory.APPLICATION),
        "sqlite" to FileType("application/vnd.sqlite3", FileCategory.APPLICATION),
        "torrent" to FileType("application/x-bittorrent", FileCategory.APPLICATION),
        "pem" to FileType("application/x-pem-file", FileCategory.APPLICATION),
        "crt" to FileType("application/x-x509-ca-cert", FileCategory.APPLICATION),
        "ttf" to FileType("font/ttf", FileCategory.APPLICATION),
        "otf" to FileType("font/otf", FileCategory.APPLICATION),
        "woff" to FileType("font/woff", FileCategory.APPLICATION),
        "woff2" to FileType("font/woff2", FileCategory.APPLICATION),
        )
    }

    fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

    fun typeOf(name: String): FileType? = TYPES[extensionOf(name)]

    fun mimeTypeOf(name: String): String = typeOf(name)?.mimeType ?: OCTET_STREAM

    fun categoryOf(name: String): FileCategory = typeOf(name)?.category ?: FileCategory.OTHER

    fun iconIdOf(name: String): String {
        val extension = extensionOf(name)
        return if (TYPES.containsKey(extension)) extension else CATEGORY_ICONS.getValue(categoryOf(name))
    }

    fun isTextual(name: String) = categoryOf(name) == FileCategory.TEXT

    fun looksLikeALogFile(name: String) = extensionOf(name) in LOG_EXTENSIONS

    fun isThumbnailable(name: String) = extensionOf(name) in THUMBNAIL_EXTENSIONS

    fun archiveFormatOf(name: String): ArchiveFormat? {
        val lower = name.lowercase()
        return ARCHIVE_SUFFIXES.firstOrNull { (suffix, _) -> lower.endsWith(suffix) && lower.length > suffix.length }
            ?.second
    }

    fun iconIds(): Set<String> = TYPES.keys + CATEGORY_ICONS.values + FOLDER_ICON + UNKNOWN_ICON
}
