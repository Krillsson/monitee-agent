package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.core.monitoring.Monitor
import com.krillsson.sysapi.core.monitoring.MonitorManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

class CheckServiceTest {

    private val repository = mockk<CheckRepository>(relaxed = true)
    private val resultRepository = mockk<CheckResultRepository>(relaxed = true)
    private val bucketRepository = mockk<CheckResultBucketRepository>(relaxed = true)
    private val probe = mockk<HttpCheckProbe>()
    private val tcpProbe = mockk<TcpCheckProbe>()
    private val pingProbe = mockk<PingCheckProbe>()
    private val dnsProbe = mockk<DnsCheckProbe>()
    private val monitorManager = mockk<MonitorManager>(relaxed = true)

    private lateinit var service: CheckService

    @BeforeEach
    fun setUp() {
        service = CheckService(repository, resultRepository, bucketRepository, probe, tcpProbe, pingProbe, dnsProbe)
        service.setMonitorManager(monitorManager)
        every { probe.probe(any()) } returns probeResult(CheckType.HTTP)
        every { tcpProbe.probe(any()) } returns probeResult(CheckType.TCP)
        every { pingProbe.probe(any()) } returns probeResult(CheckType.PING)
        every { pingProbe.unavailable } returns null
    }

    private fun probeResult(type: CheckType) = CheckResult(
        id = null,
        checkId = null,
        checkType = type,
        timestamp = Instant.parse("2026-08-01T10:00:00Z"),
        successful = true,
        latencyMs = 42,
        message = "OK",
        responseCode = if (type == CheckType.HTTP) 200 else null,
        errorBody = null,
        resolvedValues = null
    )

    companion object {
        @JvmStatic
        fun invalidSpecs(): Stream<Arguments> = Stream.of(
            Arguments.of(httpCheckSpec(url = "not a url"), "protocol"),
            Arguments.of(httpCheckSpec(intervalSeconds = 5), "Interval"),
            Arguments.of(httpCheckSpec(intervalSeconds = 30, timeoutSeconds = 60), "longer than the interval"),
            Arguments.of(httpCheckSpec(expectedStatusCodes = "nope"), "status codes"),
            Arguments.of(httpCheckSpec(headers = listOf(HttpHeader(" ", "value"))), "Header names"),
            Arguments.of(tcpCheckSpec(port = 0), "Port"),
            Arguments.of(tcpCheckSpec(port = 70000), "Port"),
            Arguments.of(tcpCheckSpec(host = " "), "host name or address is required"),
            Arguments.of(tcpCheckSpec(intervalSeconds = 5), "Interval"),
            Arguments.of(pingCheckSpec(host = "https://router.lan"), "is a URL"),
            Arguments.of(pingCheckSpec(host = "router lan"), "not a host name"),
            Arguments.of(dnsCheckSpec(hostname = " "), "host name or address is required"),
            Arguments.of(dnsCheckSpec(resolver = ""), "resolver cannot be empty"),
            Arguments.of(dnsCheckSpec(expectedValues = listOf("")), "Expected values")
        )
    }

    @ParameterizedTest
    @MethodSource("invalidSpecs")
    fun `refuses to create a check that would not work`(spec: CheckSpec, expectedReason: String) {
        // Given
        val given = spec

        // When
        val result = service.create(given)

        // Then
        result.shouldBeInstanceOf<CreateCheckResult.Fail>().reason shouldContain expectedReason
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `refuses a ping check when this system has no ping to run`() {
        // Given
        every { pingProbe.unavailable } returns "the ping command was not found on this system"

        // When
        val result = service.create(pingCheckSpec(host = "router.lan"))

        // Then
        result.shouldBeInstanceOf<CreateCheckResult.Fail>().reason shouldContain "was not found on this system"
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `refuses a host that would be handed to a shell`() {
        // Given
        val spec = pingCheckSpec(host = "router.lan; rm -rf \$HOME")

        // When
        val result = service.create(spec)

        // Then
        result.shouldBeInstanceOf<CreateCheckResult.Fail>().reason shouldContain "not a host name or address"
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `stores a valid check and answers with its new id`() {
        // Given
        val spec = httpCheckSpec(name = "Router", url = "https://router.lan", keyword = "Login")
        val saved = slot<CheckEntity>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        // When
        val result = service.create(spec)

        // Then
        val success = result.shouldBeInstanceOf<CreateCheckResult.Success>()
        saved.captured.id shouldBe success.id
        saved.captured.type shouldBe CheckType.HTTP
        saved.captured.url shouldBe "https://router.lan"
        saved.captured.keyword shouldBe "Login"
    }

    @Test
    fun `refuses to turn a check into another type`() {
        // Given
        every { repository.findById(CHECK_ID) } returns Optional.of(httpCheckEntity())

        // When
        val result = service.update(CHECK_ID, tcpCheckSpec())

        // Then
        result.shouldBeInstanceOf<UpdateCheckResult.Fail>().reason shouldContain "is a HTTP check"
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `stores what each check type needs and nothing else`() {
        // Given
        val saved = slot<CheckEntity>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        // When
        service.create(tcpCheckSpec(host = "nas.lan", port = 445))

        // Then
        saved.captured.type shouldBe CheckType.TCP
        saved.captured.host shouldBe "nas.lan"
        saved.captured.port shouldBe 445
        saved.captured.url shouldBe null
        saved.captured.expectedStatusCodes shouldBe null
    }

    @Test
    fun `probes a check with the probe for its type`() {
        // Given
        every { repository.findAll() } returns listOf(tcpCheckEntity(), pingCheckEntity(id = OTHER_CHECK_ID))

        // When
        service.runDueChecks()

        // Then
        verify(timeout = 2000, exactly = 1) { tcpProbe.probe(match { it.host == "nas.lan" && it.port == 445 }) }
        verify(timeout = 2000, exactly = 1) { pingProbe.probe(match { it.host == "router.lan" }) }
        verify(exactly = 0) { probe.probe(any()) }
    }

    @Test
    fun `falls back to the host when a check has no name`() {
        // Given
        every { repository.findById(CHECK_ID) } returns Optional.of(tcpCheckEntity(name = null))

        // When
        val check = service.getById(CHECK_ID)

        // Then
        check?.name shouldBe "nas.lan"
    }

    @Test
    fun `falls back to the url when a check has no name`() {
        // Given
        every { repository.findById(CHECK_ID) } returns Optional.of(httpCheckEntity(name = null))

        // When
        val check = service.getById(CHECK_ID)

        // Then
        check?.name shouldBe "https://example.com"
    }

    @Test
    fun `turns a check off without touching anything else about it`() {
        // Given
        every { repository.findById(CHECK_ID) } returns Optional.of(httpCheckEntity(keyword = "Login"))
        val saved = slot<CheckEntity>()
        every { repository.save(capture(saved)) } answers { saved.captured }

        // When
        val result = service.setEnabled(CHECK_ID, false)

        // Then
        result.shouldBeInstanceOf<UpdateCheckResult.Success>()
        saved.captured.enabled shouldBe false
        saved.captured.keyword shouldBe "Login"
        saved.captured.url shouldBe "https://example.com"
    }

    @Test
    fun `takes the results buckets and monitor with the check when it is deleted`() {
        // Given
        val entity = httpCheckEntity()
        every { repository.findById(CHECK_ID) } returns Optional.of(entity)
        every { repository.delete(entity) } just Runs

        // When
        val removed = service.delete(CHECK_ID)

        // Then
        removed shouldBe true
        verify { monitorManager.removeMonitorOfTypeByMonitoredItemId(Monitor.Type.WEBSERVER_UP, CHECK_ID.toString()) }
        verify { monitorManager.removeMonitorOfTypeByMonitoredItemId(Monitor.Type.CHECK_LATENCY, CHECK_ID.toString()) }
        verify { resultRepository.deleteAllByCheckId(CHECK_ID) }
        verify { bucketRepository.deleteAllByCheckId(CHECK_ID) }
    }

    @Test
    fun `attaches a stored result to the check it came from`() {
        // Given
        every { repository.findById(CHECK_ID) } returns Optional.of(httpCheckEntity())
        val saved = slot<CheckResultEntity>()
        every { resultRepository.save(capture(saved)) } answers { saved.captured }

        // When
        val result = service.runNow(CHECK_ID)

        // Then
        val success = result.shouldBeInstanceOf<RunCheckResult.Success>()
        success.result.checkId shouldBe CHECK_ID
        success.result.id shouldBe saved.captured.id
        saved.captured.successful shouldBe true
    }

    @Test
    fun `runs an enabled check that is due and leaves a disabled one alone`() {
        // Given
        every { repository.findAll() } returns listOf(
            httpCheckEntity(),
            httpCheckEntity(id = UUID.randomUUID(), enabled = false, url = "https://disabled.lan")
        )

        // When
        service.runDueChecks()

        // Then
        verify(timeout = 2000, exactly = 1) { probe.probe(match { it.url == "https://example.com" }) }
        verify(exactly = 0) { probe.probe(match { it.url == "https://disabled.lan" }) }
    }

    @Test
    fun `does not run a check again before its interval has elapsed`() {
        // Given
        every { repository.findAll() } returns listOf(httpCheckEntity(intervalSeconds = 3600))
        service.runDueChecks()
        verify(timeout = 2000, exactly = 1) { probe.probe(any()) }

        // When
        service.runDueChecks()

        // Then
        verify(exactly = 1) { probe.probe(any()) }
    }
}
