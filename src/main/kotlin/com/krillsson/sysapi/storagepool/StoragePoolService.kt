package com.krillsson.sysapi.storagepool

import com.krillsson.sysapi.config.YAMLConfigFile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class StoragePoolService(
    configuration: YAMLConfigFile,
    private val providers: List<StoragePoolProvider>
) {

    private val config = configuration.storagePools

    private val lastKnownNameById = ConcurrentHashMap<String, String>()

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }

    private fun supportedProviders(): List<StoragePoolProvider> = providers.filter { it.supported() }

    fun status(): Status {
        return when {
            !config.enabled -> Status.Disabled
            supportedProviders().isEmpty() -> Status.Unavailable(RuntimeException("No supported storage pool backend was found"))
            else -> Status.Available
        }
    }

    fun pools(): List<StoragePool> {
        return when {
            config.enabled -> supportedProviders().flatMap { it.pools() }.onEach { lastKnownNameById[it.id] = it.name }
            else -> emptyList()
        }
    }

    fun poolById(id: String): StoragePool? = pools().firstOrNull { it.id == id }

    fun displayName(id: String): String? = poolById(id)?.name ?: lastKnownNameById[id]
}
