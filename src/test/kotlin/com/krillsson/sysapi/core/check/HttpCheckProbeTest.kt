package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class HttpCheckProbeTest {

    private lateinit var server: MockWebServer
    private val probe = HttpCheckProbe()

    @BeforeEach
    fun startServer() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun url(path: String = "/") = server.url(path).toString()

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
}
