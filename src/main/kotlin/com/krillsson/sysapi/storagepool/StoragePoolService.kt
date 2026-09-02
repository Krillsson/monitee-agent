package com.krillsson.sysapi.storagepool

import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Service

@Service
class StoragePoolService(
    configuration: YAMLConfigFile,
    private val providers: List<StoragePoolProvider>
) {

    private val config = configuration.storagePools

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }

    private val supportedProviders: List<StoragePoolProvider> by lazy { providers.filter { it.supported() } }

    fun status(): Status {
        return when {
            !config.enabled -> Status.Disabled
            supportedProviders.isEmpty() -> Status.Unavailable(RuntimeException("No supported storage pool backend was found"))
            else -> Status.Available
        }
    }

    fun pools(): List<StoragePool> {
        return when (status()) {
            Status.Available -> supportedProviders.flatMap { it.pools() }
            else -> emptyList()
        }
    }

    fun poolById(id: String): StoragePool? = pools().firstOrNull { it.id == id }
}
