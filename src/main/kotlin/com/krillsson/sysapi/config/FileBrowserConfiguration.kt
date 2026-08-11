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
    val maxLogViewBytes: Long = 33_554_432
)
