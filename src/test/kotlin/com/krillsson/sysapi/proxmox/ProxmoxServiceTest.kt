package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.ObjectMapper
import com.krillsson.sysapi.config.ProxmoxConfiguration
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.scheduling.TaskScheduler
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLHandshakeException

class ProxmoxServiceTest {

    private val client = mockk<ProxmoxClient>()
    private val taskScheduler = mockk<TaskScheduler>(relaxed = true)
    private var now = Instant.parse("2026-11-11T10:00:00Z")
    private val clock = mockk<Clock> { every { instant() } answers { now } }
    private val responses = mutableMapOf<String, Result>()

    @BeforeEach
    fun setUp() {
        respond("/version", "version.json")
        respond("/cluster/resources", "cluster-resources.json")
        respond("/cluster/status", "cluster-status.json")
        respond("/nodes/pve1/status", "node-status.json")
        respond("/nodes/pve2/status", "node-status-pve8.json")
        every { client.get(any()) } answers { responses[firstArg()] ?: Result.Failed(404, "no such path ${firstArg<String>()}") }
    }

    private fun respond(path: String, fixture: String) {
        responses[path] = Result.Ok(fixtureData(fixture))
    }

    private fun service(configuration: ProxmoxConfiguration = configured()): ProxmoxService {
        val configFile = mockk<YAMLConfigFile> { every { proxmox } returns configuration }
        return ProxmoxService(configFile, client, taskScheduler, clock)
    }

    private fun configured(
        includeTemplates: Boolean = false,
        pollIntervalSeconds: Long = 15
    ) = ProxmoxConfiguration(
        enabled = true,
        host = "pve1",
        tokenId = "monitee@pve!agent",
        secret = "secret",
        includeTemplates = includeTemplates,
        pollIntervalSeconds = pollIntervalSeconds
    )

    private fun ProxmoxService.pollAfter(seconds: Long) {
        now = now.plusSeconds(seconds)
        poll()
    }

    @Test
    fun `is disabled unless enabled in the configuration`() {
        // When
        val status = service(ProxmoxConfiguration()).status()

        // Then
        status shouldBe ProxmoxService.Status.Disabled
    }

    @Test
    fun `asks for a token instead of polling when none is configured`() {
        // Given
        val service = service(ProxmoxConfiguration(enabled = true))

        // When
        service.start()

        // Then
        service.status() shouldBe ProxmoxService.Status.Unavailable("proxmox.tokenId and proxmox.secret must be set in configuration.yml")
        verify(exactly = 0) { taskScheduler.scheduleAtFixedRate(any(), any<Duration>()) }
    }

    @Test
    fun `polls no faster than every five seconds`() {
        // When
        service(configured(pollIntervalSeconds = 1)).start()

        // Then
        verify { taskScheduler.scheduleAtFixedRate(any(), Duration.ofSeconds(5)) }
    }

    @Test
    fun `is unavailable until the first poll has answered`() {
        // When
        val status = service().status()

        // Then
        status shouldBe ProxmoxService.Status.Unavailable("Waiting for the first answer from Proxmox at pve1:8006")
    }

    @Test
    fun `lists guests by vmid and leaves templates out`() {
        // Given
        val service = service()

        // When
        service.poll()

        // Then
        service.status() shouldBe ProxmoxService.Status.Available
        service.guests().map { it.vmid } shouldContainExactly listOf(100, 101, 102, 103, 104)
    }

    @Test
    fun `lists templates when asked to`() {
        // Given
        val service = service(configured(includeTemplates = true))

        // When
        service.poll()

        // Then
        service.guest("9000").shouldNotBeNull().template shouldBe true
    }

    @Test
    fun `keeps the last answer through two failed polls and gives up on the third`() {
        // Given
        val service = service()
        service.poll()
        responses["/cluster/resources"] = Result.TransportFailure(IOException("Connection refused"))

        // When
        service.pollAfter(15)
        service.pollAfter(15)
        val afterTwo = service.status()
        service.pollAfter(15)

        // Then
        afterTwo shouldBe ProxmoxService.Status.Available
        service.guests().shouldBeEmpty()
        service.status() shouldBe ProxmoxService.Status.Unavailable("Could not reach Proxmox at pve1:8006: Connection refused")
    }

    @Test
    fun `gives up at once when the token is rejected`() {
        // Given
        val service = service()
        service.poll()
        responses["/cluster/resources"] = Result.Unauthorized("authentication failure")

        // When
        service.pollAfter(15)

        // Then
        service.status().shouldBeInstanceOf<ProxmoxService.Status.Unavailable>().reason shouldContain "rejected the API token"
    }

    @Test
    fun `recovers on the next successful poll`() {
        // Given
        val service = service()
        responses["/cluster/resources"] = Result.Unauthorized("authentication failure")
        service.poll()

        // When
        respond("/cluster/resources", "cluster-resources.json")
        service.pollAfter(15)

        // Then
        service.status() shouldBe ProxmoxService.Status.Available
    }

    @Test
    fun `points at the ca setting when the certificate is not trusted`() {
        // Given
        val service = service()
        responses["/cluster/resources"] = Result.TransportFailure(SSLHandshakeException("PKIX path building failed"))

        // When
        service.poll()

        // Then
        service.status().shouldBeInstanceOf<ProxmoxService.Status.Unavailable>().reason shouldContain "proxmox.caCertificatePath"
    }

    @Test
    fun `keeps a guest that is missing from one poll and drops it after the second`() {
        // Given
        val service = service()
        service.poll()
        val withoutWeb = fixtureData("cluster-resources.json").filterNot { it.path("vmid").asInt() == 100 }
        responses["/cluster/resources"] = Result.Ok(ObjectMapper().valueToTree(withoutWeb))

        // When
        service.pollAfter(15)
        val afterOne = service.guest("100")
        service.pollAfter(15)

        // Then
        afterOne.shouldNotBeNull()
        service.guest("100").shouldBeNull()
    }

    @Test
    fun `combines the resource list, cluster status and node status into a node`() {
        // Given
        val service = service()

        // When
        service.poll()

        // Then
        val node = service.node("pve1").shouldNotBeNull()
        node.local shouldBe true
        node.ip shouldBe "192.168.1.11"
        node.cpuCount shouldBe 16
        node.cpuModel shouldBe "AMD Ryzen 7 5700G with Radeon Graphics"
        node.loadAverages shouldBe listOf(0.42, 0.38, 0.35)
        node.pveVersion shouldBe "pve-manager/9.0.3/025864202ebb6109"
        service.snapshot().shouldNotBeNull().cluster shouldBe ProxmoxCluster("homelab", quorate = true, nodeCount = 3)
        service.snapshot()!!.pveVersion shouldBe "9.0.3"
    }

    @Test
    fun `does not ask an offline node for its status`() {
        // Given
        val service = service()

        // When
        service.poll()

        // Then
        val offline = service.node("pve3").shouldNotBeNull()
        offline.status shouldBe ProxmoxNodeStatus.OFFLINE
        offline.detailUnavailableReason.shouldBeNull()
        verify(exactly = 0) { client.get("/nodes/pve3/status") }
    }

    @Test
    fun `says why a listed node's status could not be read`() {
        // Given
        responses["/nodes/pve2/status"] = Result.Failed(595, "no route to host")
        val service = service()

        // When
        service.poll()

        // Then
        service.node("pve2").shouldNotBeNull().detailUnavailableReason shouldBe "Proxmox answered 595: no route to host"
        service.status() shouldBe ProxmoxService.Status.Available
    }

    @Test
    fun `warns about a token that may not read the cluster status and still lists guests`() {
        // Given
        responses["/cluster/status"] = Result.Forbidden("Permission check failed (/, Sys.Audit)")
        val service = service()

        // When
        service.poll()

        // Then
        val snapshot = service.snapshot().shouldNotBeNull()
        snapshot.warnings.single() shouldContain "Sys.Audit on /"
        snapshot.cluster shouldBe ProxmoxCluster(name = null, quorate = true, nodeCount = 3)
        service.guests().map { it.vmid } shouldContainExactly listOf(100, 101, 102, 103, 104)
    }

    @Test
    fun `reads cluster and node status once a minute rather than every poll`() {
        // Given
        val service = service()
        service.poll()

        // When
        service.pollAfter(15)
        service.pollAfter(15)
        service.pollAfter(30)

        // Then
        verify(exactly = 4) { client.get("/cluster/resources") }
        verify(exactly = 2) { client.get("/cluster/status") }
        verify(exactly = 2) { client.get("/nodes/pve1/status") }
    }

    @Test
    fun `emits a guest's metrics to its subscribers on every poll`() {
        // Given
        val service = service()
        val emitted = mutableListOf<ProxmoxGuestMetrics>()
        val subscription = service.guestMetricsById("101").subscribe { emitted += it }

        // When
        service.poll()
        service.pollAfter(15)
        subscription.dispose()

        // Then
        emitted.map { it.id } shouldContainExactly listOf("101", "101")
    }

    @Test
    fun `explains that a vm without the guest agent has no addresses`() {
        // Given
        responses["/nodes/pve1/qemu/100/status/current"] = Result.Ok(
            ObjectMapper().readTree("""{"vmid":100,"status":"running","qmpstatus":"running"}""")
        )
        respond("/nodes/pve1/qemu/100/config", "qemu-config-no-agent.json")
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("100")!!)

        // Then
        network shouldBe ProxmoxGuestNetworkResult.Unavailable("The QEMU guest agent is not enabled for this VM")
        verify(exactly = 0) { client.get("/nodes/pve1/qemu/100/agent/network-get-interfaces") }
    }

    @Test
    fun `reads a vm's addresses through the guest agent`() {
        // Given
        respond("/nodes/pve1/qemu/100/status/current", "qemu-status-current.json")
        respond("/nodes/pve1/qemu/100/config", "qemu-config.json")
        respond("/nodes/pve1/qemu/100/agent/network-get-interfaces", "agent-network-get-interfaces.json")
        respond("/nodes/pve1/qemu/100/agent/get-host-name", "agent-get-host-name.json")
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("100")!!)

        // Then
        val available = network.shouldBeInstanceOf<ProxmoxGuestNetworkResult.Available>()
        available.network.hostname shouldBe "web01"
        available.network.interfaces.first().ipv4 shouldContainExactly listOf("192.168.1.50")
    }

    @Test
    fun `passes on why the guest agent did not answer`() {
        // Given
        respond("/nodes/pve1/qemu/100/status/current", "qemu-status-current.json")
        respond("/nodes/pve1/qemu/100/config", "qemu-config.json")
        responses["/nodes/pve1/qemu/100/agent/network-get-interfaces"] = Result.Failed(500, "QEMU guest agent is not running")
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("100")!!)

        // Then
        network shouldBe ProxmoxGuestNetworkResult.Unavailable("QEMU guest agent is not running")
    }

    @Test
    fun `names the privilege a token lacks for the guest agent`() {
        // Given
        respond("/nodes/pve1/qemu/100/status/current", "qemu-status-current.json")
        respond("/nodes/pve1/qemu/100/config", "qemu-config.json")
        responses["/nodes/pve1/qemu/100/agent/network-get-interfaces"] = Result.Forbidden("Permission check failed")
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("100")!!)

        // Then
        network.shouldBeInstanceOf<ProxmoxGuestNetworkResult.Unavailable>().reason shouldContain "VM.GuestAgent.Audit"
    }

    @Test
    fun `reads a container's addresses with the hostname from its config`() {
        // Given
        respond("/nodes/pve1/lxc/101/status/current", "lxc-status-current.json")
        respond("/nodes/pve1/lxc/101/config", "lxc-config.json")
        respond("/nodes/pve1/lxc/101/interfaces", "lxc-interfaces.json")
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("101")!!)

        // Then
        val available = network.shouldBeInstanceOf<ProxmoxGuestNetworkResult.Available>()
        available.network.hostname shouldBe "pihole"
        available.network.interfaces.single().ipv4 shouldContainExactly listOf("192.168.1.53")
    }

    @Test
    fun `does not ask a stopped guest for its addresses`() {
        // Given
        val service = service()
        service.poll()

        // When
        val network = service.guestNetwork(service.guest("102")!!)

        // Then
        network shouldBe ProxmoxGuestNetworkResult.Unavailable("The guest is not running")
    }
}
