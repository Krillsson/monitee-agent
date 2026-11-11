package com.krillsson.sysapi.proxmox

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ProxmoxJsonTest {

    private val resources = ProxmoxJson.resources(fixtureData("cluster-resources.json"))

    private fun guest(vmid: Int) = resources.guests.single { it.vmid == vmid }

    @Test
    fun `reads qemu and lxc guests and leaves pools and sdn rows out`() {
        // When
        val vmids = resources.guests.map { it.vmid to it.kind }

        // Then
        vmids shouldContainExactly listOf(
            100 to ProxmoxGuestKind.QEMU,
            101 to ProxmoxGuestKind.LXC,
            102 to ProxmoxGuestKind.QEMU,
            103 to ProxmoxGuestKind.QEMU,
            9000 to ProxmoxGuestKind.QEMU,
            104 to ProxmoxGuestKind.LXC
        )
        resources.nodes.map { it.name } shouldContainExactly listOf("pve1", "pve2", "pve3")
        resources.storages.map { it.id } shouldContainExactly listOf("pve1/local", "pve1/nas", "pve3/local")
    }

    @Test
    fun `uses the vmid as the guest id`() {
        // When
        val web = guest(100)

        // Then
        web.id shouldBe "100"
        web.name shouldBe "web"
        web.node shouldBe "pve1"
    }

    @Test
    fun `turns the cpu fraction into a percentage of the guest's own cpus`() {
        // When
        val metrics = guest(100).metrics.shouldNotBeNull()

        // Then
        metrics.cpuUsage.usagePercentTotal shouldBe (25.0 plusOrMinus 0.0001)
        guest(100).cpuCount shouldBe 4
    }

    @Test
    fun `computes memory usage against the guest's maximum`() {
        // When
        val memory = guest(100).metrics.shouldNotBeNull().memoryUsage

        // Then
        memory.usageBytes shouldBe 2147483648
        memory.limitBytes shouldBe 4294967296
        memory.usagePercent shouldBe (50.0 plusOrMinus 0.0001)
        memory.hostUsageBytes shouldBe 2684354560
    }

    @Test
    fun `reports no disk usage for a vm, where proxmox always says zero`() {
        // When
        val vm = guest(100).metrics.shouldNotBeNull()
        val container = guest(101).metrics.shouldNotBeNull()

        // Then
        vm.diskUsage.shouldBeNull()
        container.diskUsage.shouldNotBeNull().usageBytes shouldBe 1073741824
        container.diskUsage!!.limitBytes shouldBe 8589934592
    }

    @Test
    fun `keeps traffic and block io counters as reported`() {
        // When
        val metrics = guest(100).metrics.shouldNotBeNull()

        // Then
        metrics.networkUsage.bytesReceived shouldBe 123456789
        metrics.networkUsage.bytesTransferred shouldBe 987654321
        metrics.blockIOUsage.bytesRead shouldBe 5555555
        metrics.blockIOUsage.bytesWritten shouldBe 6666666
    }

    @ParameterizedTest
    @CsvSource(
        "100, RUNNING",
        "102, STOPPED",
        "104, UNKNOWN"
    )
    fun `maps the guest status`(vmid: Int, expected: ProxmoxGuestStatus) {
        // When
        val status = guest(vmid).status

        // Then
        status shouldBe expected
    }

    @Test
    fun `has no metrics for a guest that is not running`() {
        // When
        val stopped = guest(102)
        val unknown = guest(104)

        // Then
        stopped.metrics.shouldBeNull()
        unknown.metrics.shouldBeNull()
    }

    @Test
    fun `splits tags and drops an empty tag string`() {
        // When
        val tagged = guest(100).tags
        val untagged = guest(101).tags

        // Then
        tagged shouldContainExactly listOf("prod", "web")
        untagged.shouldBeEmpty()
    }

    @Test
    fun `leaves ha state and lock null when proxmox does not report them`() {
        // When
        val free = guest(100)
        val locked = guest(103)

        // Then
        free.haState.shouldBeNull()
        free.lock.shouldBeNull()
        locked.haState shouldBe "started"
        locked.lock shouldBe "backup"
    }

    @Test
    fun `marks templates`() {
        // When
        val template = guest(9000)

        // Then
        template.template shouldBe true
        guest(100).template shouldBe false
    }

    @Test
    fun `names a guest after its vmid when proxmox gives no name`() {
        // Given
        val data = ObjectMapper().readTree(
            """[{"type":"lxc","vmid":105,"node":"pve1","status":"stopped"}]"""
        )

        // When
        val guests = ProxmoxJson.resources(data).guests

        // Then
        guests.single().name shouldBe "CT 105"
    }

    @Test
    fun `reads an offline node with nothing but its status`() {
        // When
        val offline = resources.nodes.single { it.name == "pve3" }

        // Then
        offline.status shouldBe ProxmoxNodeStatus.OFFLINE
        offline.cpuUsagePercent.shouldBeNull()
        offline.memoryTotalBytes.shouldBeNull()
    }

    @Test
    fun `reads node usage from the resource list`() {
        // When
        val node = resources.nodes.single { it.name == "pve1" }

        // Then
        node.status shouldBe ProxmoxNodeStatus.ONLINE
        node.cpuUsagePercent.shouldNotBeNull() shouldBe (4.21 plusOrMinus 0.0001)
        node.cpuCount shouldBe 16
        node.memoryUsedBytes shouldBe 13421772800
        node.uptimeSeconds shouldBe 864000
    }

    @Test
    fun `reads storage type, sharing and content`() {
        // When
        val nas = resources.storages.single { it.id == "pve1/nas" }
        val unknown = resources.storages.single { it.id == "pve3/local" }

        // Then
        nas.type shouldBe "nfs"
        nas.shared shouldBe true
        nas.status shouldBe ProxmoxStorageStatus.AVAILABLE
        nas.content shouldContainExactly listOf("images", "backup")
        nas.usedBytes shouldBe 500000000000
        unknown.status shouldBe ProxmoxStorageStatus.UNKNOWN
        unknown.type.shouldBeNull()
        unknown.shared shouldBe false
        unknown.content.shouldBeEmpty()
    }

    @Test
    fun `reads the cluster name, quorum and members`() {
        // When
        val status = ProxmoxJson.clusterStatus(fixtureData("cluster-status.json"))

        // Then
        status.cluster shouldBe ProxmoxCluster(name = "homelab", quorate = true, nodeCount = 3)
        status.nodes["pve1"] shouldBe ProxmoxJson.ClusterNode(ip = "192.168.1.11", local = true)
        status.nodes["pve2"] shouldBe ProxmoxJson.ClusterNode(ip = "192.168.1.12", local = false)
    }

    @Test
    fun `reports a lost quorum`() {
        // When
        val status = ProxmoxJson.clusterStatus(fixtureData("cluster-status-no-quorum.json"))

        // Then
        status.cluster.quorate shouldBe false
    }

    @Test
    fun `treats a status without a cluster row as a quorate standalone node`() {
        // When
        val status = ProxmoxJson.clusterStatus(fixtureData("cluster-status-standalone.json"))

        // Then
        status.cluster shouldBe ProxmoxCluster(name = null, quorate = true, nodeCount = 1)
        status.nodes["pve"]?.local shouldBe true
    }

    @Test
    fun `reads node details including the load averages proxmox sends as strings`() {
        // When
        val detail = ProxmoxJson.nodeStatus(fixtureData("node-status.json"))

        // Then
        detail.loadAverages shouldBe listOf(0.42, 0.38, 0.35)
        detail.cpuModel shouldBe "AMD Ryzen 7 5700G with Radeon Graphics"
        detail.cpuCount shouldBe 16
        detail.ksmSharedBytes shouldBe 1073741824
        detail.rootFsUsedBytes shouldBe 10737418240
        detail.rootFsTotalBytes shouldBe 100000000000
        detail.pveVersion shouldBe "pve-manager/9.0.3/025864202ebb6109"
        detail.kernelRelease shouldBe "6.14.8-2-pve"
    }

    @Test
    fun `falls back to kversion and leaves ksm null on a node that reports neither`() {
        // When
        val detail = ProxmoxJson.nodeStatus(fixtureData("node-status-pve8.json"))

        // Then
        detail.ksmSharedBytes.shouldBeNull()
        detail.kernelRelease shouldBe "Linux 6.8.12-10-pve #1 SMP PREEMPT_DYNAMIC PMX 6.8.12-10 (2025-04-18T07:39Z)"
    }

    @Test
    fun `reads the version`() {
        // When
        val version = ProxmoxJson.version(fixtureData("version.json"))

        // Then
        version shouldBe "9.0.3"
    }

    @Test
    fun `reads a vm's run state, agent and os type`() {
        // When
        val detail = ProxmoxJson.guestDetail(
            ProxmoxGuestKind.QEMU,
            fixtureData("qemu-status-current.json"),
            fixtureData("qemu-config.json")
        )

        // Then
        detail.qmpStatus shouldBe "running"
        detail.agentEnabled shouldBe true
        detail.osType shouldBe "l26"
        detail.unprivileged.shouldBeNull()
        detail.swapTotalBytes.shouldBeNull()
    }

    @Test
    fun `reads a vm without an agent line as having no agent`() {
        // When
        val detail = ProxmoxJson.guestDetail(
            ProxmoxGuestKind.QEMU,
            fixtureData("qemu-status-current-stopped.json"),
            fixtureData("qemu-config-no-agent.json")
        )

        // Then
        detail.qmpStatus shouldBe "stopped"
        detail.agentEnabled shouldBe false
        detail.osType shouldBe "win11"
    }

    @Test
    fun `reads a container's hostname, privileges and swap`() {
        // When
        val detail = ProxmoxJson.guestDetail(
            ProxmoxGuestKind.LXC,
            fixtureData("lxc-status-current.json"),
            fixtureData("lxc-config.json")
        )

        // Then
        detail.qmpStatus.shouldBeNull()
        detail.agentEnabled.shouldBeNull()
        detail.hostname shouldBe "pihole"
        detail.osType shouldBe "debian"
        detail.unprivileged shouldBe true
        detail.swapUsedBytes shouldBe 1048576
        detail.swapTotalBytes shouldBe 536870912
    }

    @ParameterizedTest
    @CsvSource(
        "1, true",
        "0, false",
        "'1,fstrim_cloned_disks=1', true",
        "'enabled=1,type=virtio', true",
        "'enabled=0', false",
        "'type=isa', false"
    )
    fun `reads the agent option in each form proxmox accepts`(value: String, expected: Boolean) {
        // When
        val enabled = ProxmoxJson.agentEnabled(value)

        // Then
        enabled shouldBe expected
    }

    @Test
    fun `reads a vm's addresses from the guest agent, without loopback`() {
        // When
        val network = ProxmoxJson.qemuNetwork(
            fixtureData("agent-network-get-interfaces.json"),
            fixtureData("agent-get-host-name.json")
        )

        // Then
        network.hostname shouldBe "web01"
        network.interfaces.map { it.name } shouldContainExactly listOf("ens18", "docker0")
        network.interfaces.first() shouldBe ProxmoxGuestNetworkInterface(
            name = "ens18",
            macAddress = "bc:24:11:aa:bb:cc",
            ipv4 = listOf("192.168.1.50"),
            ipv6 = listOf("fe80::be24:11ff:feaa:bbcc")
        )
    }

    @Test
    fun `reads a container's addresses, whose types are inet and inet6`() {
        // When
        val network = ProxmoxJson.lxcNetwork(fixtureData("lxc-interfaces.json"), "pihole")

        // Then
        network.hostname shouldBe "pihole"
        network.interfaces shouldContainExactly listOf(
            ProxmoxGuestNetworkInterface(
                name = "eth0",
                macAddress = "bc:24:11:11:22:33",
                ipv4 = listOf("192.168.1.53"),
                ipv6 = listOf("fe80::be24:11ff:fe11:2233")
            )
        )
    }

    @Test
    fun `falls back to the inet fields on a container listing without ip-addresses`() {
        // When
        val network = ProxmoxJson.lxcNetwork(fixtureData("lxc-interfaces-legacy.json"), null)

        // Then
        network.interfaces.single().ipv4 shouldContainExactly listOf("10.0.0.5")
        network.interfaces.single().ipv6.shouldBeEmpty()
    }
}
