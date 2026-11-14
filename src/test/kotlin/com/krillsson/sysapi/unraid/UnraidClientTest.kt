package com.krillsson.sysapi.unraid

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.UnraidConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.unraid.UnraidClient.Result
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class UnraidClientTest {

    private val server = MockWebServer()

    @BeforeEach
    fun startServer() {
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun client(): UnraidClient {
        val configFile = mockk<YAMLConfigFile> {
            every { unraid } returns UnraidConfiguration(
                enabled = true,
                host = server.hostName,
                port = server.port,
                apiKey = "a1b2c3-monitee_key"
            )
        }
        return UnraidClient(configFile, ObjectMapper())
    }

    private fun json(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `posts the vm query to graphql with the api key`() {
        // Given
        server.enqueue(json("""{"data":{"vms":{"domains":[]}}}"""))

        // When
        client().vmDomains()

        // Then
        val request = server.takeRequest()
        request.method shouldBe "POST"
        request.path shouldBe "/graphql"
        request.getHeader("x-api-key") shouldBe "a1b2c3-monitee_key"
        request.getHeader("Content-Type") shouldBe "application/json; charset=utf-8"
        ObjectMapper().readTree(request.body.readUtf8()).path("query").asText() shouldBe
            "query { vms { domains { id name state } } }"
    }

    @Test
    fun `reads every domain unraid lists`() {
        // Given
        server.enqueue(
            json(
                """{"data":{"vms":{"domains":[
                    {"id":"f3a9c1e2d4b5:$WINDOWS_UUID","name":"Windows 11","state":"RUNNING"},
                    {"id":"$HOME_ASSISTANT_UUID","name":null,"state":"SHUTOFF","uuid":"$HOME_ASSISTANT_UUID"}
                ]}}}"""
            )
        )

        // When
        val result = client().vmDomains()

        // Then
        result.shouldBeInstanceOf<Result.Ok<List<UnraidApi.VmDomain>>>().data shouldContainExactly listOf(
            UnraidApi.VmDomain("f3a9c1e2d4b5:$WINDOWS_UUID", "Windows 11", "RUNNING"),
            UnraidApi.VmDomain(HOME_ASSISTANT_UUID, null, "SHUTOFF")
        )
    }

    @Test
    fun `reports a 401 as a rejected key`() {
        // Given
        server.enqueue(json("""{"message":"Unauthorized","statusCode":401}""", status = 401))

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Unauthorized("Unauthorized")
    }

    @ParameterizedTest
    @ValueSource(strings = ["UNAUTHENTICATED", "UNAUTHORIZED"])
    fun `reports an unauthenticated graphql error answered with 200 as a rejected key`(code: String) {
        // Given
        server.enqueue(
            json("""{"errors":[{"message":"Unauthorized","extensions":{"code":"$code"}}],"data":null}""")
        )

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Unauthorized("Unauthorized")
    }

    @Test
    fun `reports a forbidden graphql error as missing permission`() {
        // Given
        server.enqueue(
            json("""{"errors":[{"message":"Forbidden resource","extensions":{"code":"FORBIDDEN"}}],"data":null}""")
        )

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Forbidden("Forbidden resource")
    }

    @Test
    fun `reports the vm manager being stopped as a graphql error rather than an empty list`() {
        // Given
        server.enqueue(
            json(
                """{"errors":[{"message":"VMs are not available","path":["vms","domains"],
                "extensions":{"code":"INTERNAL_SERVER_ERROR"}}],"data":{"vms":{"domains":null}}}"""
            )
        )

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.GraphQlErrors(listOf("VMs are not available"), listOf("INTERNAL_SERVER_ERROR"))
    }

    @Test
    fun `reports a validation error from an api without vms, answered with 400`() {
        // Given
        server.enqueue(
            json(
                """{"errors":[{"message":"Cannot query field \"vms\" on type \"Query\".",
                "extensions":{"code":"GRAPHQL_VALIDATION_FAILED"}}]}""",
                status = 400
            )
        )

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.GraphQlErrors(
            listOf("Cannot query field \"vms\" on type \"Query\"."),
            listOf("GRAPHQL_VALIDATION_FAILED")
        )
    }

    @Test
    fun `reports a rate limit as a failed call`() {
        // Given
        server.enqueue(json("""{"statusCode":429,"message":"ThrottlerException: Too Many Requests"}""", status = 429))

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Failed(429, "ThrottlerException: Too Many Requests")
    }

    @Test
    fun `does not follow a redirect to https, which would turn the post into a get`() {
        // Given
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://tower.local/graphql"))

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Redirected(302, "https://tower.local/graphql")
        server.requestCount shouldBe 1
    }

    @Test
    fun `reports an answer without the domain list as failed`() {
        // Given
        server.enqueue(json("""{"data":{"vms":null}}"""))

        // When
        val result = client().vmDomains()

        // Then
        result shouldBe Result.Failed(200, "Unraid sent an answer without vms.domains")
    }

    @Test
    fun `reports an unreachable server as a transport failure`() {
        // Given
        val client = client()
        server.shutdown()

        // When
        val result = client.vmDomains()

        // Then
        result.shouldBeInstanceOf<Result.TransportFailure>()
    }
}
