package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

class HttpCheckProbeTest {

    private lateinit var server: MockWebServer
    private val httpsServers = mutableListOf<MockWebServer>()
    private val probe = HttpCheckProbe()

    private val rootCertificate = HeldCertificate.Builder()
        .certificateAuthority(0)
        .commonName("Monitee Test Root")
        .build()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
        httpsServers.forEach { it.shutdown() }
    }

    private fun url(path: String = "/") = server.url(path).toString()

    private fun httpsServer(
        commonName: String = "expiring.test",
        issuedAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        expiresAt: Instant = Instant.parse("2099-01-01T00:00:00Z")
    ): MockWebServer {
        val leaf = HeldCertificate.Builder()
            .commonName(commonName)
            .addSubjectAlternativeName("localhost")
            .validityInterval(issuedAt.toEpochMilli(), expiresAt.toEpochMilli())
            .signedBy(rootCertificate)
            .build()
        val certificates = HandshakeCertificates.Builder()
            .heldCertificate(leaf, rootCertificate.certificate)
            .build()
        return MockWebServer().apply {
            useHttps(certificates.sslSocketFactory(), false)
            enqueue(MockResponse().setResponseCode(200))
            start()
            httpsServers.add(this)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "200-299, 200, true",
        "200-299, 404, false",
        "404, 404, true",
        "'200-299,301', 301, true"
    )
    fun `decides success from the expected status codes`(expected: String, responseCode: Int, successful: Boolean) {
        // Given
        server.enqueue(MockResponse().setResponseCode(responseCode))
        val spec = httpCheckSpec(url = url(), expectedStatusCodes = expected)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe successful
        result.responseCode shouldBe responseCode
    }

    @ParameterizedTest
    @CsvSource(
        "Example Domain, false, true",
        "Missing text,   false, false",
        "Example Domain, true,  false",
        "Missing text,   true,  true"
    )
    fun `asserts on the response body`(keyword: String, inverted: Boolean, successful: Boolean) {
        // Given
        server.enqueue(MockResponse().setResponseCode(200).setBody("<h1>Example Domain</h1>"))
        val spec = httpCheckSpec(url = url(), keyword = keyword, keywordInverted = inverted)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe successful
    }

    @Test
    fun `sends the configured method and headers`() {
        // Given
        server.enqueue(MockResponse().setResponseCode(200))
        val spec = httpCheckSpec(
            url = url(),
            method = HttpMethod.HEAD,
            headers = listOf(HttpHeader("Authorization", "Bearer token"))
        )

        // When
        probe.probe(spec)

        // Then
        val request = server.takeRequest()
        request.method shouldBe "HEAD"
        request.getHeader("Authorization") shouldBe "Bearer token"
    }

    @Test
    fun `reports the redirect itself when the check does not follow redirects`() {
        // Given
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", url("/moved")))
        val spec = httpCheckSpec(url = url(), followRedirects = false)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.responseCode shouldBe 302
    }

    @Test
    fun `reports no response code when the host cannot be reached`() {
        // Given
        val unreachable = url()
        server.shutdown()
        val spec = httpCheckSpec(url = unreachable, timeoutSeconds = 2)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.responseCode shouldBe -1
        result.message shouldContain "onnect"
    }

    @Test
    fun `captures the leaf certificate of an HTTPS response`() {
        // Given
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val expiresAt = Instant.parse("2099-03-04T05:06:07Z")
        val https = httpsServer(commonName = "expiring.test", issuedAt = issuedAt, expiresAt = expiresAt)
        val spec = httpCheckSpec(url = https.url("/").toString(), ignoreCertificateErrors = true)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe true
        result.certificateExpiresAt shouldBe expiresAt
        result.certificateIssuedAt shouldBe issuedAt
        result.certificateIssuer shouldBe "Monitee Test Root"
        result.certificateSubject shouldBe "expiring.test"
    }

    @Test
    fun `captures no certificate over plain HTTP`() {
        // Given
        server.enqueue(MockResponse().setResponseCode(200))
        val spec = httpCheckSpec(url = url())

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe true
        result.certificate() shouldBe null
        result.certificateIssuedAt shouldBe null
        result.certificateIssuer shouldBe null
        result.certificateSubject shouldBe null
    }

    @Test
    fun `captures no certificate when the handshake is refused`() {
        // Given
        val https = httpsServer()
        val spec = httpCheckSpec(url = https.url("/").toString(), ignoreCertificateErrors = false, timeoutSeconds = 5)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe false
        result.certificateExpiresAt shouldBe null
        result.certificateIssuedAt shouldBe null
        result.certificateIssuer shouldBe null
        result.certificateSubject shouldBe null
    }

    @Test
    fun `captures an expired certificate when certificate errors are ignored`() {
        // Given
        val expiresAt = Instant.parse("2026-02-01T00:00:00Z")
        val https = httpsServer(issuedAt = Instant.parse("2026-01-01T00:00:00Z"), expiresAt = expiresAt)
        val spec = httpCheckSpec(url = https.url("/").toString(), ignoreCertificateErrors = true)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe true
        result.certificateExpiresAt shouldBe expiresAt
    }

    @Test
    fun `reports the certificate of the host a redirect ends at`() {
        // Given
        val https = httpsServer(commonName = "final.test")
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", https.url("/landing")))
        val spec = httpCheckSpec(url = url(), followRedirects = true, ignoreCertificateErrors = true)

        // When
        val result = probe.probe(spec)

        // Then
        result.successful shouldBe true
        result.certificateSubject shouldBe "final.test"
    }
}
