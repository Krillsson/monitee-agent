package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorManager
import com.krillsson.sysapi.util.logger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.jvm.optionals.getOrNull

@Service
class CheckService(
    private val repository: CheckRepository,
    private val resultRepository: CheckResultRepository,
    private val bucketRepository: CheckResultBucketRepository,
    private val httpProbe: HttpCheckProbe
) {
    companion object {
        const val DEFAULT_INTERVAL_SECONDS = 60
        const val DEFAULT_TIMEOUT_SECONDS = 20
        const val DEFAULT_EXPECTED_STATUS_CODES = "200-299"
        private const val MIN_INTERVAL_SECONDS = 10
        private const val MAX_TIMEOUT_SECONDS = 300
        private const val RUNNER_THREADS = 4
    }

    private val logger by logger()
    private val lastRunAt = ConcurrentHashMap<UUID, Instant>()
    private val inFlight = ConcurrentHashMap.newKeySet<UUID>()
    private val runners = Executors.newFixedThreadPool(RUNNER_THREADS) { runnable ->
        Thread(runnable, "check-runner").apply { isDaemon = true }
    }

    private lateinit var monitorManager: MonitorManager

    @Autowired
    fun setMonitorManager(@Lazy monitorManager: MonitorManager) {
        this.monitorManager = monitorManager
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.SECONDS)
    fun runDueChecks() {
        val now = Instant.now()
        dueChecks(now).forEach { check ->
            lastRunAt[check.id] = now
            if (inFlight.add(check.id)) {
                runners.execute {
                    try {
                        store(check, probe(check))
                    } finally {
                        inFlight.remove(check.id)
                    }
                }
            }
        }
    }

    fun getAll(): List<Check> = repository.findAll().map { it.asDomain() }

    fun getById(id: UUID): Check? = repository.findById(id).getOrNull()?.asDomain()

    fun latestResult(id: UUID): CheckResult? =
        resultRepository.findFirstByCheckIdOrderByTimestampDesc(id).getOrNull()?.asDomain()

    fun createHttpCheck(spec: HttpCheckSpec): CreateCheckResult {
        validate(spec)?.let { return CreateCheckResult.Fail(it) }
        val id = UUID.randomUUID()
        repository.save(spec.asEntity(id))
        logger.debug("Added HTTP check for {}", spec.url)
        return CreateCheckResult.Success(id)
    }

    fun updateHttpCheck(id: UUID, spec: HttpCheckSpec): UpdateCheckResult {
        val existing = repository.findById(id).getOrNull()
            ?: return UpdateCheckResult.Fail("No check with id $id was found")
        if (existing.type != CheckType.HTTP) {
            return UpdateCheckResult.Fail("Check $id is a ${existing.type} check")
        }
        validate(spec)?.let { return UpdateCheckResult.Fail(it) }
        repository.save(spec.asEntity(id))
        return UpdateCheckResult.Success(id)
    }

    fun setEnabled(id: UUID, enabled: Boolean): UpdateCheckResult {
        val existing = repository.findById(id).getOrNull()
            ?: return UpdateCheckResult.Fail("No check with id $id was found")
        repository.save(existing.copyWithEnabled(enabled))
        return UpdateCheckResult.Success(id)
    }

    @Transactional
    fun delete(id: UUID): Boolean {
        val entity = repository.findById(id).getOrNull()
        if (entity == null) {
            logger.warn("No check with id {} was found", id)
            return false
        }
        logger.debug("Deleting check: {}", entity.displayName())
        monitorManager.removeMonitorOfTypeByMonitoredItemId(Monitor.Type.WEBSERVER_UP, id.toString())
        repository.delete(entity)
        resultRepository.deleteAllByCheckId(id)
        bucketRepository.deleteAllByCheckId(id)
        lastRunAt.remove(id)
        return true
    }

    fun runNow(id: UUID): RunCheckResult {
        val check = getById(id) ?: return RunCheckResult.Fail("No check with id $id was found")
        lastRunAt[id] = Instant.now()
        return RunCheckResult.Success(store(check, probe(check)))
    }

    fun runOneOff(spec: HttpCheckSpec): CheckResult = httpProbe.probe(spec)

    private fun dueChecks(now: Instant): List<Check> = getAll().filter { check ->
        check.enabled && lastRunAt[check.id]?.let {
            Duration.between(it, now).seconds >= check.intervalSeconds
        } ?: true
    }

    private fun probe(check: Check): CheckResult = when (check) {
        is HttpCheck -> httpProbe.probe(check.asSpec())
    }

    private fun store(check: Check, result: CheckResult): CheckResult {
        val stored = result.copy(id = UUID.randomUUID(), checkId = check.id)
        resultRepository.save(
            CheckResultEntity(
                id = requireNotNull(stored.id),
                checkId = check.id,
                checkType = stored.checkType,
                timestamp = stored.timestamp,
                successful = stored.successful,
                latencyMs = stored.latencyMs,
                message = stored.message,
                responseCode = stored.responseCode,
                errorBody = stored.errorBody
            )
        )
        return stored
    }

    private fun validate(spec: HttpCheckSpec): String? = validateUrl(spec.url) ?: when {
        spec.intervalSeconds < MIN_INTERVAL_SECONDS -> "Interval must be at least $MIN_INTERVAL_SECONDS seconds"
        spec.timeoutSeconds !in 1..MAX_TIMEOUT_SECONDS -> "Timeout must be between 1 and $MAX_TIMEOUT_SECONDS seconds"
        spec.timeoutSeconds > spec.intervalSeconds -> "Timeout cannot be longer than the interval"
        StatusCodeRanges.parse(spec.expectedStatusCodes) == null ->
            "\"${spec.expectedStatusCodes}\" is not a list of status codes or ranges such as 200-299,301"

        spec.headers.any { it.name.isBlank() } -> "Header names cannot be empty"
        else -> null
    }

    private fun validateUrl(url: String): String? = try {
        URL(url).toURI()
        null
    } catch (e: MalformedURLException) {
        e.message ?: e::class.java.simpleName
    } catch (e: URISyntaxException) {
        e.message ?: e::class.java.simpleName
    }

    private fun HttpCheckSpec.asEntity(id: UUID) = CheckEntity(
        id = id,
        type = CheckType.HTTP,
        name = name?.takeIf { it.isNotBlank() },
        enabled = enabled,
        intervalSeconds = intervalSeconds,
        timeoutSeconds = timeoutSeconds,
        url = url,
        method = method,
        expectedStatusCodes = expectedStatusCodes,
        keyword = keyword?.takeIf { it.isNotEmpty() },
        keywordInverted = keywordInverted,
        ignoreCertificateErrors = ignoreCertificateErrors,
        followRedirects = followRedirects,
        headers = headers.takeIf { it.isNotEmpty() }
    )

    private fun CheckEntity.copyWithEnabled(enabled: Boolean) = CheckEntity(
        id = id,
        type = type,
        name = name,
        enabled = enabled,
        intervalSeconds = intervalSeconds,
        timeoutSeconds = timeoutSeconds,
        url = url,
        method = method,
        expectedStatusCodes = expectedStatusCodes,
        keyword = keyword,
        keywordInverted = keywordInverted,
        ignoreCertificateErrors = ignoreCertificateErrors,
        followRedirects = followRedirects,
        headers = headers
    )

    private fun CheckEntity.displayName() = name ?: url ?: id.toString()

    private fun CheckEntity.asDomain(): Check = when (type) {
        CheckType.HTTP -> HttpCheck(
            id = id,
            name = displayName(),
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            url = url.orEmpty(),
            method = method ?: HttpMethod.GET,
            expectedStatusCodes = expectedStatusCodes ?: DEFAULT_EXPECTED_STATUS_CODES,
            keyword = keyword,
            keywordInverted = keywordInverted,
            ignoreCertificateErrors = ignoreCertificateErrors,
            followRedirects = followRedirects,
            headers = headers.orEmpty()
        )
    }
}

fun HttpCheck.asSpec() = HttpCheckSpec(
    name = name,
    enabled = enabled,
    intervalSeconds = intervalSeconds,
    timeoutSeconds = timeoutSeconds,
    url = url,
    method = method,
    expectedStatusCodes = expectedStatusCodes,
    keyword = keyword,
    keywordInverted = keywordInverted,
    ignoreCertificateErrors = ignoreCertificateErrors,
    followRedirects = followRedirects,
    headers = headers
)
