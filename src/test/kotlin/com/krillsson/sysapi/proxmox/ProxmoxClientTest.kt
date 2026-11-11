package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import javax.net.ssl.SSLException

class ProxmoxClientTest {

    private val rootCertificate = HeldCertificate.Builder()
        .certificateAuthority(0)
        .commonName("Proxmox Virtual Environment")
        .build()

    private val server = MockWebServer()

    @TempDir
    lateinit var directory: File

    @BeforeEach
    fun startServer() {
        val leaf = HeldCertificate.Builder()
            .commonName("pve1")
            .addSubjectAlternativeName("localhost")
            .signedBy(rootCertificate)
            .build()
        val certificates = HandshakeCertificates.Builder()
            .heldCertificate(leaf, rootCertificate.certificate)
            .build()
        server.useHttps(certificates.sslSocketFactory(), false)
        server.start()
    }

    @AfterEach
    fun stopServer() {
        server.shutdown()
    }

    private fun client(verifyTls: Boolean = false, caCertificatePath: String? = null): ProxmoxClient {
        val configFile = mockk<YAMLConfigFile> {
            every { proxmox } returns ProxmoxConfiguration(
                enabled = true,
                host = server.hostName,
                port = server.port,
                tokenId = "monitee@pve!agent",
                secret = "0b6a1c1e-5c2f-4c5d-9c1e-3d2f1a0b9c8d",
                verifyTls = verifyTls,
                caCertificatePath = caCertificatePath
            )
        }
        return ProxmoxClient(configFile, ObjectMapper())
    }

    @Test
    fun `authenticates with the api token and reads data into the requested type`() {
        // Given
        server.enqueue(MockResponse().setBody(fixtureBody("version.json")))

        // When
        val result = client().get("/version", ProxmoxApi.Version::class.java)

        // Then
        val request = server.takeRequest()
        request.path shouldBe "/api2/json/version"
        request.getHeader("Authorization") shouldBe "PVEAPIToken=monitee@pve!agent=0b6a1c1e-5c2f-4c5d-9c1e-3d2f1a0b9c8d"
        result.shouldBeInstanceOf<ProxmoxClient.Result.Ok<ProxmoxApi.Version>>().data.version shouldBe "9.0.3"
    }

    @Test
    fun `keeps every path segment`() {
        // Given
        server.enqueue(MockResponse().setBody("""{"data":{}}"""))

        // When
        client().get("/nodes/pve1/qemu/100/agent/network-get-interfaces", ProxmoxApi.AgentInterfaces::class.java)

        // Then
        server.takeRequest().path shouldBe "/api2/json/nodes/pve1/qemu/100/agent/network-get-interfaces"
    }

    @Test
    fun `reports a rejected token with the reason phrase proxmox sends instead of a body`() {
        // Given
        server.enqueue(MockResponse().setStatus("HTTP/1.1 401 authentication failure"))

        // When
        val result = client().getList("/cluster/resources", ProxmoxApi.Resource::class.java)

        // Then
        result shouldBe ProxmoxClient.Result.Unauthorized("authentication failure")
    }

    @Test
    fun `prefers the message in the body over the reason phrase`() {
        // Given
        server.enqueue(
            MockResponse()
                .setStatus("HTTP/1.1 403 Permission check failed")
                .setBody("""{"data":null,"message":"Permission check failed (/, Sys.Audit)\n"}""")
        )

        // When
        val result = client().getList("/cluster/status", ProxmoxApi.ClusterStatusEntry::class.java)

        // Then
        result shouldBe ProxmoxClient.Result.Forbidden("Permission check failed (/, Sys.Audit)")
    }

    @ParameterizedTest
    @CsvSource(
        "500, QEMU guest agent is not running",
        "500, No QEMU guest agent configured",
        "595, no route to host",
        "596, Connection timed out"
    )
    fun `reads the error from the reason phrase when the body has none`(status: Int, reason: String) {
        // Given
        server.enqueue(MockResponse().setStatus("HTTP/1.1 $status $reason").setBody("""{"data":null}"""))

        // When
        val result = client().get("/nodes/pve2/status", ProxmoxApi.NodeStatus::class.java)

        // Then
        result shouldBe ProxmoxClient.Result.Failed(status, reason)
    }

    @Test
    fun `names the parameters a 400 complains about`() {
        // Given
        server.enqueue(
            MockResponse()
                .setStatus("HTTP/1.1 400 Parameter verification failed.")
                .setBody("""{"data":null,"errors":{"vmid":"invalid format - value does not look like a valid VM ID\n"}}""")
        )

        // When
        val result = client().get("/nodes/pve1/qemu/abc/status/current", ProxmoxApi.GuestStatus::class.java)

        // Then
        result shouldBe ProxmoxClient.Result.Failed(400, "vmid: invalid format - value does not look like a valid VM ID")
    }

    @Test
    fun `reports data of an unexpected shape as a failure`() {
        // Given
        server.enqueue(MockResponse().setBody("""{"data":[1,2,3]}"""))

        // When
        val result = client().get("/nodes/pve1/status", ProxmoxApi.NodeStatus::class.java)

        // Then
        val failed = result.shouldBeInstanceOf<ProxmoxClient.Result.Failed>()
        failed.status shouldBe 200
        failed.reason shouldStartWith "Proxmox sent an answer the agent could not read as NodeStatus: "
    }

    @Test
    fun `refuses the self-signed certificate when verifying tls without a ca`() {
        // Given
        server.enqueue(MockResponse().setBody(fixtureBody("version.json")))

        // When
        val result = client(verifyTls = true).get("/version", ProxmoxApi.Version::class.java)

        // Then
        result.shouldBeInstanceOf<ProxmoxClient.Result.TransportFailure>().error.shouldBeInstanceOf<SSLException>()
    }

    @Test
    fun `trusts the certificate when the proxmox ca is configured`() {
        // Given
        val caFile = File(directory, "pve-root-ca.pem").apply { writeText(rootCertificate.certificatePem()) }
        server.enqueue(MockResponse().setBody(fixtureBody("version.json")))

        // When
        val result = client(verifyTls = true, caCertificatePath = caFile.path).get("/version", ProxmoxApi.Version::class.java)

        // Then
        result.shouldBeInstanceOf<ProxmoxClient.Result.Ok<ProxmoxApi.Version>>()
    }

    @Test
    fun `reports a ca file without certificates as a failure`() {
        // Given
        val caFile = File(directory, "empty.pem").apply { writeText("") }

        // When
        val result = client(verifyTls = true, caCertificatePath = caFile.path).get("/version", ProxmoxApi.Version::class.java)

        // Then
        result.shouldBeInstanceOf<ProxmoxClient.Result.TransportFailure>()
    }
}
