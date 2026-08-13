package com.krillsson.sysapi.config

enum class FileBrowserAccess {
    READ,
    READ_WRITE
}

data class FileBrowserConfiguration(
    val enabled: Boolean = false,
    val access: FileBrowserAccess = FileBrowserAccess.READ,
    val roots: List<String> = emptyList(),
    val maxEditableBytes: Long = 1_048_576,
    val maxUploadBytes: Long = 0,
    val maxLogViewBytes: Long = 33_554_432,
    val trash: Boolean = true,
    val searchTimeoutSeconds: Long = 20,
    val thumbnails: Boolean = true,
    val maxThumbnailSourceBytes: Long = 67_108_864,
    val maxThumbnailCacheBytes: Long = 268_435_456,
    val maxArchiveEntries: Int = 100_000,
    val maxArchiveBytes: Long = 21_474_836_480
)
