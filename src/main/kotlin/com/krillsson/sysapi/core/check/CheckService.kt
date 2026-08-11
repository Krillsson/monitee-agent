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
    private val httpProbe: HttpCheckProbe,
    private val tcpProbe: TcpCheckProbe,
    private val pingProbe: PingCheckProbe
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

    fun create(spec: CheckSpec): CreateCheckResult {
        validate(spec)?.let { return CreateCheckResult.Fail(it) }
        val id = UUID.randomUUID()
        val entity = repository.save(spec.asEntity(id))
        logger.debug("Added {} check for {}", spec.type, entity.displayName())
        return CreateCheckResult.Success(id)
    }

    fun update(id: UUID, spec: CheckSpec): UpdateCheckResult {
        val existing = repository.findById(id).getOrNull()
            ?: return UpdateCheckResult.Fail("No check with id $id was found")
        if (existing.type != spec.type) {
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

    fun runOneOff(spec: CheckSpec): CheckResult = probe(spec)

    private fun dueChecks(now: Instant): List<Check> = getAll().filter { check ->
        check.enabled && lastRunAt[check.id]?.let {
            Duration.between(it, now).seconds >= check.intervalSeconds
        } ?: true
    }

    private fun probe(check: Check): CheckResult = probe(check.asSpec())

    private fun probe(spec: CheckSpec): CheckResult = when (spec) {
        is HttpCheckSpec -> httpProbe.probe(spec)
        is TcpCheckSpec -> tcpProbe.probe(spec)
        is PingCheckSpec -> pingProbe.probe(spec)
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

    private fun validate(spec: CheckSpec): String? = validateSchedule(spec) ?: when (spec) {
        is HttpCheckSpec -> validateUrl(spec.url) ?: when {
            StatusCodeRanges.parse(spec.expectedStatusCodes) == null ->
                "\"${spec.expectedStatusCodes}\" is not a list of status codes or ranges such as 200-299,301"

            spec.headers.any { it.name.isBlank() } -> "Header names cannot be empty"
            else -> null
        }

        is TcpCheckSpec -> validateHost(spec.host) ?: when {
            spec.port !in 1..65535 -> "Port must be between 1 and 65535"
            else -> null
        }

        is PingCheckSpec -> validateHost(spec.host)
    }

    private fun validateSchedule(spec: CheckSpec): String? = when {
        spec.intervalSeconds < MIN_INTERVAL_SECONDS -> "Interval must be at least $MIN_INTERVAL_SECONDS seconds"
        spec.timeoutSeconds !in 1..MAX_TIMEOUT_SECONDS -> "Timeout must be between 1 and $MAX_TIMEOUT_SECONDS seconds"
        spec.timeoutSeconds > spec.intervalSeconds -> "Timeout cannot be longer than the interval"
        else -> null
    }

    private fun validateHost(host: String): String? = when {
        host.isBlank() -> "A host name or address is required"
        host.any { it.isWhitespace() } -> "\"$host\" is not a host name or address"
        host.contains("/") -> "\"$host\" is a URL, not a host name or address"
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

    private fun CheckSpec.asEntity(id: UUID): CheckEntity = when (this) {
        is HttpCheckSpec -> CheckEntity(
            id = id,
            type = type,
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

        is TcpCheckSpec -> CheckEntity(
            id = id,
            type = type,
            name = name?.takeIf { it.isNotBlank() },
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            host = host,
            port = port
        )

        is PingCheckSpec -> CheckEntity(
            id = id,
            type = type,
            name = name?.takeIf { it.isNotBlank() },
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            host = host
        )
    }

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
        headers = headers,
        host = host,
        port = port
    )

    private fun CheckEntity.displayName() = name ?: url ?: host ?: id.toString()

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

        CheckType.TCP -> TcpCheck(
            id = id,
            name = displayName(),
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            host = host.orEmpty(),
            port = port ?: 0
        )

        CheckType.PING -> PingCheck(
            id = id,
            name = displayName(),
            enabled = enabled,
            intervalSeconds = intervalSeconds,
            timeoutSeconds = timeoutSeconds,
            host = host.orEmpty()
        )
    }
}

fun Check.asSpec(): CheckSpec = when (this) {
    is HttpCheck -> HttpCheckSpec(
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

    is TcpCheck -> TcpCheckSpec(
        name = name,
        enabled = enabled,
        intervalSeconds = intervalSeconds,
        timeoutSeconds = timeoutSeconds,
        host = host,
        port = port
    )

    is PingCheck -> PingCheckSpec(
        name = name,
        enabled = enabled,
        intervalSeconds = intervalSeconds,
        timeoutSeconds = timeoutSeconds,
        host = host
    )
}
