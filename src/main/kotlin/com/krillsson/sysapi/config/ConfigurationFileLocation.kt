package com.krillsson.sysapi.config

import com.krillsson.sysapi.USER_CONFIG
import com.krillsson.sysapi.util.FileSystem
import java.io.File

object ConfigurationFileLocation {

    const val IMPORT_PROPERTY = "spring.config.import"
    private const val FILE_IMPORT = "optional:file:./config/$USER_CONFIG"
    private const val BUNDLED_IMPORT = "optional:classpath:/config/$USER_CONFIG"

    fun import(configFile: File = File(FileSystem.config, USER_CONFIG)): String = when {
        configFile.exists() -> FILE_IMPORT
        else -> BUNDLED_IMPORT
    }

    fun defaultProperties(configFile: File = File(FileSystem.config, USER_CONFIG)): Map<String, Any> =
        mapOf(IMPORT_PROPERTY to import(configFile))
}
