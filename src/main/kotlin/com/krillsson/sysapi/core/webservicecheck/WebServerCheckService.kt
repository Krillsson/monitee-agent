package com.krillsson.sysapi.core.webservicecheck

import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.util.logger
import okhttp3.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull


@Service
class WebServerCheckService(
    private val repository: WebServerCheckRepository,
    private val webServerUptimeCalculator: WebServerUptimeCalculator,
    private val historyRepository: WebServerHistoryEntryRepository,
    yamlConfigFile: YAMLConfigFile,
) {
    private val logger by logger()

    private val client = OkHttpClient.Builder()
        .readTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build();
    private val purgeConfig = yamlConfigFile.metricsConfig.history.purging

    private lateinit var monitorManager: MonitorManager

    @Autowired
    fun setMonitorManager(@Lazy monitorManager: MonitorManager) {
        this.monitorManager = monitorManager
    }

    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.SECONDS)
    @Transactional
    fun run() {
        checkAvailabilities()
        purgeOutOfDateEntries()
    }

    fun checkNow(url: String): OneOffWebserverResult {
        val start = Instant.now()
        return try {
            val request: Request = Request.Builder()
                .url(url)
                .build()
            val call = client.newCall(request)
            val response = call.execute()
            val end = Instant.now()
            val latencyMs = Duration.between(start, end).toMillis()
            logger.debug("Request ${if (response.isSuccessful) "SUCCESS" else "FAIL"} $url: ${response.code} - ${response.message} (${latencyMs}ms)")
            OneOffWebserverResult(
                Instant.now(),
                response.code,
                response.message,
                latencyMs,
                response.readLimitedBody()
            )
        } catch (e: Exception) {
            val errorMessage = e.message ?: e::class.java.simpleName
            val end = Instant.now()
            val latencyMs = Duration.between(start, end).toMillis()
            logger.warn("Request FAIL ${url}: $errorMessage (${latencyMs}ms)")
            OneOffWebserverResult(
                Instant.now(),
                -1,
                errorMessage,
                latencyMs,
                null
            )
        }
    }

    fun runWebServerCheckNow(id: UUID): WebServerCheckHistoryEntry? {
        val start = Instant.now()
        val entity = repository.findById(id).getOrNull()
        val status = entity?.let { check ->
            try {
                val request: Request = Request.Builder()
                    .url(check.url)
                    .build()
                val call = client.newCall(request)
                val response = call.execute()
                val end = Instant.now()
                val latencyMs = Duration.between(start, end).toMillis()
                logger.debug("Request ${if (response.isSuccessful) "SUCCESS" else "FAIL"} $check.url: ${response.code} - ${response.message} (${latencyMs}ms)")
                WebServerCheckHistoryEntity(
                    UUID.randomUUID(),
                    entity.id,
                    Instant.now(),
                    response.code,
                    latencyMs,
                    response.message,
                    response.readLimitedBody()
                )
            } catch (e: Exception) {
                val errorMessage = e.message ?: e::class.java.simpleName
                val end = Instant.now()
                val latencyMs = Duration.between(start, end).toMillis()
                logger.warn("Request FAIL ${check.url}: $errorMessage (${latencyMs}ms)")
                WebServerCheckHistoryEntity(
                    UUID.randomUUID(),
                    entity.id,
                    Instant.now(),
                    -1,
                    latencyMs,
                    errorMessage,
                    null
                )
            }
        }
        return if (status != null) {
            historyRepository.save(status)
            status.asDomain()
        } else {
            logger.warn("No webserver check with id $id was found")
            null
        }
    }

    fun addWebServer(url: String): AddWebServerResult {
        return when (val validationResult = validateUrl(url)) {
            URLValidationResult.Valid -> {
                val id = UUID.randomUUID()
                repository.save(WebServerCheckEntity(id, url))
                logger.debug("Added $url webserver check")
                AddWebServerResult.Success(id)
            }

            is URLValidationResult.Invalid -> {
                logger.warn("URL $url is invalid: ${validationResult.message}")
                AddWebServerResult.Fail(validationResult.message)
            }
        }
    }

    @Transactional
    fun removeWebServerById(id: UUID): Boolean {
        val entity = repository.findById(id)
        if (entity.isPresent) {
            logger.debug("Deleting webserver check: ${entity.get().url}")
            monitorManager.removeMonitorOfTypeByMonitoredItemId(Monitor.Type.WEBSERVER_UP, id.toString())
            repository.delete(entity.get())
            historyRepository.deleteByWebServerCheckId(id)
        } else {
            logger.warn("No webserver check with id $id was found")
        }
        return entity.isPresent
    }

    fun getAll(): List<WebServerCheck> {
        return repository.findAll().map { it.asDomain() }
    }

    fun getById(id: UUID): WebServerCheck? {
        return repository.findById(id).map { it.asDomain() }.getOrNull()
    }

    fun getStatusForWebServer(id: UUID): WebServerCheckHistoryEntry? {
        return historyRepository.findFirstByWebServerCheckIdOrderByTimeStampDesc(id)
            .map {
                it.asDomain()
            }.getOrNull()
    }

    fun getHistoryForWebServerBetweenTimestamps(id: UUID, from: Instant, to: Instant): List<WebServerCheckHistoryEntry> {
        return historyRepository.findByTimeStampBetweenAndWebServerCheckIdIs(from, to, id)
            .map {
                it.asDomain()
            }
    }

    fun getUptimeMetricsForWebServer(webserverId: UUID): UptimeMetrics? {
        val now = Instant.now()
        val beginningOfPeriod = now.minus(purgeConfig.olderThan, purgeConfig.unit)
        val historyThisMonth: List<WebServerCheckHistoryEntry> = getHistoryForWebServerBetweenTimestamps(webserverId, beginningOfPeriod, now)
        return webServerUptimeCalculator.metricsForHistory(historyThisMonth)
    }


    private fun checkAvailabilities() {
        repository.findAll()
            .forEach {
                logger.debug("Checking availability: ${it.url}")
                checkAvailable(it)
            }
    }

    private fun purgeOutOfDateEntries() {
        val maxAge = Instant.now()
            .minus(purgeConfig.olderThan, purgeConfig.unit)
        val deletedCount = historyRepository.deleteByTimeStampBefore(maxAge)
        logger.debug("Purged $deletedCount history older than {}", maxAge)
    }

    private fun checkAvailable(entity: WebServerCheckEntity) {
        val request: Request = Request.Builder()
            .url(entity.url)
            .build()

        val call: Call = client.newCall(request)
        val start = Instant.now()
        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val end = Instant.now()
                val latencyMs = Duration.between(start, end).toMillis()
                logger.debug("Request ${if (response.isSuccessful) "SUCCESS" else "FAIL"} ${entity.url}: ${response.code} - ${response.message} (${latencyMs}ms)")
                val entity = WebServerCheckHistoryEntity(
                    UUID.randomUUID(),
                    entity.id,
                    Instant.now(),
                    response.code,
                    latencyMs,
                    response.message,
                    response.readLimitedBody()
                )
                historyRepository.save(
                    entity
                )
            }

            override fun onFailure(call: Call, e: IOException) {
                val errorMessage = e.message ?: e::class.java.simpleName
                val end = Instant.now()
                val latencyMs = Duration.between(start, end).toMillis()
                logger.warn("Request FAIL ${entity.url}: $errorMessage (${latencyMs}ms)")
                val entity = WebServerCheckHistoryEntity(
                    UUID.randomUUID(),
                    entity.id,
                    Instant.now(),
                    -1,
                    latencyMs,
                    errorMessage,
                    null
                )
                historyRepository.save(
                    entity
                )
            }
        })
    }

    private fun Response.readLimitedBody(): String {
        val bodyString = try {
            peekBody(512).use {
                it.string()
            }
        } catch (exception: Exception) {
            "${exception::class.simpleName}: ${exception.message}"
        }
        body?.close()
        return bodyString
    }

    fun WebServerCheckHistoryEntity.asDomain(): WebServerCheckHistoryEntry {
        return with(this) {
            WebServerCheckHistoryEntry(
                id = id,
                webserverCheckId = webServerCheckId,
                timeStamp = timeStamp,
                responseCode = responseCode,
                latencyMs = latencyMs,
                message = message,
                errorBody = errorBody
            )
        }
    }

    private fun WebServerCheckEntity.asDomain(): WebServerCheck {
        return with(this) {
            WebServerCheck(id, url)
        }
    }

    sealed interface URLValidationResult {
        data class Invalid(val message: String) : URLValidationResult
        data object Valid : URLValidationResult
    }

    fun validateUrl(url: String): URLValidationResult {
        try {
            URL(url).toURI()
            return URLValidationResult.Valid
        } catch (e: MalformedURLException) {
            return URLValidationResult.Invalid(e.message ?: e::class.java.simpleName)
        } catch (e: URISyntaxException) {
            return URLValidationResult.Invalid(e.message ?: e::class.java.simpleName)
        }
    }
}
