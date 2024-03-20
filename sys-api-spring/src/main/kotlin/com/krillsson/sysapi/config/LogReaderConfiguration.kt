package com.krillsson.sysapi.config

data class LogReaderConfiguration(
    var files: List<String> = emptyList(),
    var directories: List<String> = emptyList()
)
