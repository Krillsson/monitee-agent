package com.krillsson.sysapi.serverid

import com.krillsson.sysapi.persistence.KeyValueRepository
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Service
import java.util.*


@Service
class ServerIdService(val keyValueRepository: KeyValueRepository) {

    private val logger by logger()

    val serverId: UUID by lazy {
        getOrCreateServerId()
    }

    fun getOrCreateServerId(): UUID {
        val existingId = keyValueRepository.get(SERVER_ID_KEY)
        return existingId?.asUUID() ?: run {
            val newServerId = UUID.randomUUID()
            logger.info("Server ID '$SERVER_ID_KEY' not set. Creating new ID: $newServerId")
            keyValueRepository.put(SERVER_ID_KEY, newServerId.toString())
            newServerId
        }
    }


    companion object {
        private const val SERVER_ID_KEY = "serverId"
    }

    private fun String.asUUID(): UUID {
        return UUID.fromString(this)
    }
}
