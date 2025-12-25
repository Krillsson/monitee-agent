package com.krillsson.sysapi.core.connectivity

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.domain.network.Connectivity
import com.krillsson.sysapi.persistence.KeyValueRepository
import com.krillsson.sysapi.util.logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Sinks
import retrofit2.Response
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

@Service
class ConnectivityCheckService(
    private val externalIpAddressService: ExternalIpAddressService,
    private val repository: KeyValueRepository,
    private val yamlConfigFile: YAMLConfigFile
) {

    private val config = yamlConfigFile.connectivityCheck

    private val logger by logger()

    private var _connectivity: Connectivity = defaultConnectivity

    @Scheduled(fixedRate = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (config.enabled) {
            _connectivity = resolveConnectivity()
        }
    }


    private fun resolveConnectivity(): Connectivity {
        val response: Response<String> = try {
            externalIpAddressService.getMyIp().execute()
        } catch (e: Exception) {
            Response.error(503, ResponseBody.create("text/plain".toMediaType(), ""))
        }
        val localIp = findLocalIp()
        val storedExternalIp = repository.get(externalIpStoreKey)
        val newExternalIp = response.body()
        return if (response.isSuccessful && newExternalIp != null) {
            logger.debug("Connectivity check: CONNECTED - external IP $newExternalIp (old: $storedExternalIp)")
            repository.put(externalIpStoreKey, newExternalIp)
            Connectivity(
                externalIp = newExternalIp,
                previousExternalIp = storedExternalIp,
                localIp = localIp.hostAddress,
                connected = true
            )
        } else {
            logger.debug(
                "Connectivity check: DISCONNECTED - got ${response.code()}/${
                    response.body().orEmpty()
                } from ${yamlConfigFile.connectivityCheck.address}. stored ip: $storedExternalIp"
            )
            Connectivity(
                externalIp = null,
                previousExternalIp = storedExternalIp,
                localIp = localIp.hostAddress,
                connected = false
            )
        }
    }

    fun getConnectivity() = _connectivity

    fun findLocalIp(): InetAddress {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress("google.com", 80))
            socket.localAddress
        } catch (exception: Exception) {
            val localHost = InetAddress.getLocalHost()
            logger.error("Exception while resolving local IP, resorting to $localHost", exception)
            localHost
        }
    }

    companion object {
        const val externalIpStoreKey = "externalIp"
        val defaultConnectivity = Connectivity(null, null, null, false)
    }
}