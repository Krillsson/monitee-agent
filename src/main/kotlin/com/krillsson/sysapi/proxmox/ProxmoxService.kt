package com.krillsson.sysapi.proxmox

import com.google.common.base.Ticker
import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.krillsson.sysapi.config.YAMLConfigFile
import com.krillsson.sysapi.proxmox.ProxmoxClient.Result
import com.krillsson.sysapi.util.logger
import jakarta.annotation.PostConstruct
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.TimeUnit

@Service
class ProxmoxService(
    configuration: YAMLConfigFile,
    private val client: ProxmoxClient,
    private val taskScheduler: TaskScheduler,
    private val clock: Clock
) {
    companion object {
        const val FAILURES_BEFORE_UNAVAILABLE = 3
        private const val MINIMUM_POLL_INTERVAL_SECONDS = 5L
        private val DETAIL_INTERVAL: Duration = Duration.ofMinutes(1)
        private const val NETWORK_CACHE_MINUTES = 5L
    }

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val reason: String) : Status()
    }

    private data class NodeDetailState(val detail: ProxmoxConverter.NodeDetail?, val unavailableReason: String?)

    private val logger by logger()

    private val config = configuration.proxmox

    private val pollInterval = Duration.ofSeconds(config.pollIntervalSeconds.coerceAtLeast(MINIMUM_POLL_INTERVAL_SECONDS))

    @Volatile
    private var snapshot: ProxmoxSnapshot? = null

    @Volatile
    private var consecutiveFailures = 0

    @Volatile
    private var lastFailure: String? = null

    private var missingGuestIds = emptySet<String>()
    private var version: String? = null
    private var clusterStatus: ProxmoxConverter.ClusterStatus? = null
    private var nodeDetails = emptyMap<String, NodeDetailState>()
    private var warnings = emptyList<String>()
    private var detailsRefreshedAt: Instant? = null

    private val ticker = object : Ticker() {
        override fun read(): Long = clock.instant().let { TimeUnit.SECONDS.toNanos(it.epochSecond) + it.nano }
    }

    private val guestDetails: Cache<String, Optional<ProxmoxGuestDetail>> = CacheBuilder.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(pollInterval.seconds, TimeUnit.SECONDS)
        .build()

    private val guestNetworks: Cache<String, ProxmoxGuestNetworkResult.Available> = CacheBuilder.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(NETWORK_CACHE_MINUTES, TimeUnit.MINUTES)
        .build()

    private val unavailableGuestNetworks: Cache<String, ProxmoxGuestNetworkResult.Unavailable> = CacheBuilder.newBuilder()
        .ticker(ticker)
        .expireAfterWrite(pollInterval.seconds, TimeUnit.SECONDS)
        .build()

    private val guestMetricsSink = Sinks.many()
        .multicast()
        .directBestEffort<List<ProxmoxGuestMetrics>>()

    @PostConstruct
    fun start() {
        if (!config.enabled || configurationError() != null) {
            return
        }
        if (!config.verifyTls) {
            logger.warn("proxmox.verifyTls is false: the agent will accept any certificate from {}:{}", config.host, config.port)
        }
        taskScheduler.scheduleAtFixedRate(this::poll, pollInterval)
    }

    fun status(): Status {
        return when {
            !config.enabled -> Status.Disabled
            else -> configurationError()?.let { Status.Unavailable(it) }
                ?: when {
                    snapshot == null || consecutiveFailures >= FAILURES_BEFORE_UNAVAILABLE -> Status.Unavailable(
                        lastFailure ?: "Waiting for the first answer from Proxmox at ${config.host}:${config.port}"
                    )

                    else -> Status.Available
                }
        }
    }

    fun snapshot(): ProxmoxSnapshot? = snapshot.takeIf { status() == Status.Available }

    fun guests(): List<ProxmoxGuest> = snapshot()?.guests.orEmpty()

    fun guest(id: String): ProxmoxGuest? = guests().firstOrNull { it.id == id }

    fun nodes(): List<ProxmoxNode> = snapshot()?.nodes.orEmpty()

    fun node(name: String): ProxmoxNode? = nodes().firstOrNull { it.name == name }

    fun storages(): List<ProxmoxStorage> = snapshot()?.storages.orEmpty()

    fun guestMetricsById(id: String): Flux<ProxmoxGuestMetrics> = Mono.defer { Mono.justOrEmpty(guest(id)?.metrics) }
        .concatWith(guestMetricsSink.asFlux().mapNotNull { metrics -> metrics.firstOrNull { it.id == id } })

    fun guestDetail(guest: ProxmoxGuest): ProxmoxGuestDetail? =
        guestDetails.get(guest.cacheKey()) { Optional.ofNullable(fetchDetail(guest)) }.orElse(null)

    fun guestNetwork(guest: ProxmoxGuest): ProxmoxGuestNetworkResult {
        val key = guest.cacheKey()
        guestNetworks.getIfPresent(key)?.let { return it }
        unavailableGuestNetworks.getIfPresent(key)?.let { return it }
        return when (val network = fetchNetwork(guest)) {
            is ProxmoxGuestNetworkResult.Available -> network.also { guestNetworks.put(key, it) }
            is ProxmoxGuestNetworkResult.Unavailable -> network.also { unavailableGuestNetworks.put(key, it) }
        }
    }

    fun supportedCommands(guest: ProxmoxGuest): List<ProxmoxGuestCommand> =
        ProxmoxGuestCommands.supported(guest, quorate(), qmpStatus(guest))

    fun commandRefusal(guest: ProxmoxGuest, command: ProxmoxGuestCommand): String? =
        ProxmoxGuestCommands.refusal(guest, command, quorate(), qmpStatus(guest))

    fun guestChanged(guest: ProxmoxGuest) {
        guestDetails.asMap().keys.removeIf { it.startsWith("${guest.id}:") }
        guestNetworks.asMap().keys.removeIf { it.startsWith("${guest.id}:") }
        unavailableGuestNetworks.asMap().keys.removeIf { it.startsWith("${guest.id}:") }
        poll()
    }

    @Synchronized
    fun poll() {
        try {
            when (val result = client.getList("/cluster/resources", ProxmoxApi.Resource::class.java)) {
                is Result.Ok -> refresh(ProxmoxConverter.resources(result.data))
                is Result.Failure -> recordFailure(result)
            }
        } catch (exception: Exception) {
            logger.warn("Failed to poll Proxmox", exception)
            consecutiveFailures++
            lastFailure = exception.message ?: exception::class.java.simpleName
        }
    }

    private fun refresh(resources: ProxmoxConverter.Resources) {
        val now = clock.instant()
        if (snapshot == null || consecutiveFailures > 0 || detailsDue(now)) {
            refreshDetails(resources.nodes, now)
        }
        val guests = retainMissingOnce(resources.guests.filter { config.includeTemplates || !it.template })
        snapshot = ProxmoxSnapshot(
            cluster = clusterStatus?.cluster ?: ProxmoxCluster(name = null, quorate = true, nodeCount = resources.nodes.size),
            nodes = resources.nodes.map { node ->
                val state = nodeDetails[node.name]
                ProxmoxConverter.node(node, clusterStatus?.nodes?.get(node.name), state?.detail, state?.unavailableReason)
            }.sortedBy { it.name },
            guests = guests,
            storages = resources.storages.sortedBy { it.id },
            warnings = warnings,
            pveVersion = version ?: "unknown",
            lastSuccessfulPoll = now
        )
        consecutiveFailures = 0
        lastFailure = null
        if (guestMetricsSink.currentSubscriberCount() > 0) {
            guestMetricsSink.tryEmitNext(guests.mapNotNull { it.metrics })
        }
    }

    private fun retainMissingOnce(current: List<ProxmoxGuest>): List<ProxmoxGuest> {
        val currentIds = current.map { it.id }.toSet()
        val retained = snapshot?.guests.orEmpty().filter { it.id !in currentIds && it.id !in missingGuestIds }
        missingGuestIds = retained.map { it.id }.toSet()
        return (current + retained).sortedBy { it.vmid }
    }

    private fun detailsDue(now: Instant): Boolean =
        detailsRefreshedAt?.let { Duration.between(it, now) >= DETAIL_INTERVAL } ?: true

    private fun refreshDetails(nodes: List<ProxmoxConverter.ResourceNode>, now: Instant) {
        val warnings = mutableListOf<String>()
        (client.get("/version", ProxmoxApi.Version::class.java) as? Result.Ok)?.let { version = ProxmoxConverter.version(it.data) }
        when (val result = client.getList("/cluster/status", ProxmoxApi.ClusterStatusEntry::class.java)) {
            is Result.Ok -> clusterStatus = ProxmoxConverter.clusterStatus(result.data)
            is Result.Forbidden -> {
                clusterStatus = null
                warnings += "The API token lacks Sys.Audit on /: the cluster name, quorum and node addresses are hidden"
            }

            else -> Unit
        }
        nodeDetails = nodes.associate { node ->
            node.name to when (node.status) {
                ProxmoxNodeStatus.ONLINE -> nodeDetail(node.name, warnings)
                else -> NodeDetailState(detail = null, unavailableReason = null)
            }
        }
        this.warnings = warnings
        detailsRefreshedAt = now
    }

    private fun nodeDetail(name: String, warnings: MutableList<String>): NodeDetailState {
        return when (val result = client.get("/nodes/$name/status", ProxmoxApi.NodeStatus::class.java)) {
            is Result.Ok -> NodeDetailState(ProxmoxConverter.nodeDetail(result.data), unavailableReason = null)
            is Result.Forbidden -> {
                warnings += "The API token lacks Sys.Audit on /nodes/$name: the details of node $name are hidden"
                NodeDetailState(detail = null, unavailableReason = result.reason)
            }

            is Result.Failure -> NodeDetailState(detail = null, unavailableReason = describe(result))
        }
    }

    private fun recordFailure(result: Result.Failure) {
        val reason = describe(result)
        logger.debug("Proxmox poll failed: {}", reason)
        consecutiveFailures = when (result) {
            is Result.Unauthorized -> maxOf(consecutiveFailures + 1, FAILURES_BEFORE_UNAVAILABLE)
            else -> consecutiveFailures + 1
        }
        lastFailure = reason
    }

    private fun describe(result: Result.Failure): String = ProxmoxConverter.failureReason(result, config.host, config.port)

    private fun configurationError(): String? = when {
        config.tokenId.isBlank() || config.secret.isBlank() -> "proxmox.tokenId and proxmox.secret must be set in configuration.yml"
        config.caCertificatePath != null && !File(config.caCertificatePath).isFile ->
            "proxmox.caCertificatePath ${config.caCertificatePath} is not a file"

        else -> null
    }

    private fun fetchDetail(guest: ProxmoxGuest): ProxmoxGuestDetail? {
        val status = (client.get("${guest.apiPath()}/status/current", ProxmoxApi.GuestStatus::class.java) as? Result.Ok)?.data
            ?: return null
        val guestConfig = (client.get("${guest.apiPath()}/config", ProxmoxApi.GuestConfig::class.java) as? Result.Ok)?.data
            ?: return null
        return ProxmoxConverter.guestDetail(guest.kind, status, guestConfig)
    }

    private fun fetchNetwork(guest: ProxmoxGuest): ProxmoxGuestNetworkResult {
        if (guest.status != ProxmoxGuestStatus.RUNNING) {
            return ProxmoxGuestNetworkResult.Unavailable("The guest is not running")
        }
        return when (guest.kind) {
            ProxmoxGuestKind.QEMU -> qemuNetwork(guest)
            ProxmoxGuestKind.LXC -> when (val interfaces = client.getList("${guest.apiPath()}/interfaces", ProxmoxApi.LxcInterface::class.java)) {
                is Result.Ok -> ProxmoxGuestNetworkResult.Available(
                    ProxmoxConverter.lxcNetwork(interfaces.data, guestDetail(guest)?.hostname)
                )

                is Result.Failure -> ProxmoxGuestNetworkResult.Unavailable(describe(interfaces))
            }
        }
    }

    private fun qemuNetwork(guest: ProxmoxGuest): ProxmoxGuestNetworkResult {
        val detail = guestDetail(guest)
            ?: return ProxmoxGuestNetworkResult.Unavailable("Could not read the status and configuration of this VM from Proxmox")
        if (detail.agentEnabled != true) {
            return ProxmoxGuestNetworkResult.Unavailable("The QEMU guest agent is not enabled for this VM")
        }
        return when (val interfaces = client.get("${guest.apiPath()}/agent/network-get-interfaces", ProxmoxApi.AgentInterfaces::class.java)) {
            is Result.Ok -> ProxmoxGuestNetworkResult.Available(
                ProxmoxConverter.qemuNetwork(
                    interfaces.data,
                    (client.get("${guest.apiPath()}/agent/get-host-name", ProxmoxApi.AgentHostName::class.java) as? Result.Ok)?.data
                )
            )

            is Result.Forbidden -> ProxmoxGuestNetworkResult.Unavailable(
                "The API token lacks VM.GuestAgent.Audit (VM.Monitor on Proxmox VE 8)"
            )

            is Result.Failed -> ProxmoxGuestNetworkResult.Unavailable(interfaces.reason.replaceFirstChar { it.uppercase() })
            is Result.Failure -> ProxmoxGuestNetworkResult.Unavailable(describe(interfaces))
        }
    }

    private fun quorate(): Boolean = snapshot()?.cluster?.quorate ?: false

    private fun qmpStatus(guest: ProxmoxGuest): String? = when {
        guest.kind == ProxmoxGuestKind.QEMU && guest.status == ProxmoxGuestStatus.RUNNING -> guestDetail(guest)?.qmpStatus
        else -> null
    }

    private fun ProxmoxGuest.cacheKey() = "$id:$node:$status"
}
