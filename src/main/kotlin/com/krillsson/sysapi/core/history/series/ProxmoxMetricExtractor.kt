package com.krillsson.sysapi.core.history.series

import com.krillsson.sysapi.proxmox.ProxmoxGuest
import com.krillsson.sysapi.proxmox.ProxmoxGuestStatus
import com.krillsson.sysapi.proxmox.ProxmoxNode
import java.time.Instant

object ProxmoxMetricExtractor {

    fun guests(guests: List<ProxmoxGuest>, timestamp: Instant): List<MetricSample> =
        guests.filterNot { it.template }.flatMap { guest ->
            buildList {
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_RUNNING,
                        guest.id,
                        timestamp,
                        (guest.status == ProxmoxGuestStatus.RUNNING).asFlag()
                    )
                )
                val metrics = guest.metrics ?: return@buildList
                add(sample(MetricId.PROXMOX_GUEST_CPU_PERCENT, guest.id, timestamp, metrics.cpuUsage.usagePercentTotal))
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_MEMORY_USED_BYTES,
                        guest.id,
                        timestamp,
                        metrics.memoryUsage.usageBytes.toDouble()
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_MEMORY_TOTAL_BYTES,
                        guest.id,
                        timestamp,
                        metrics.memoryUsage.limitBytes.toDouble()
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_MEMORY_USED_PERCENT,
                        guest.id,
                        timestamp,
                        metrics.memoryUsage.usagePercent
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_RX_BYTES,
                        guest.id,
                        timestamp,
                        metrics.networkUsage.bytesReceived.toDouble()
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_TX_BYTES,
                        guest.id,
                        timestamp,
                        metrics.networkUsage.bytesTransferred.toDouble()
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_BLOCK_READ_BYTES,
                        guest.id,
                        timestamp,
                        metrics.blockIOUsage.bytesRead.toDouble()
                    )
                )
                add(
                    sample(
                        MetricId.PROXMOX_GUEST_BLOCK_WRITE_BYTES,
                        guest.id,
                        timestamp,
                        metrics.blockIOUsage.bytesWritten.toDouble()
                    )
                )
                metrics.diskUsage?.let {
                    add(sample(MetricId.PROXMOX_GUEST_DISK_USED_BYTES, guest.id, timestamp, it.usageBytes.toDouble()))
                }
            }
        }

    fun nodes(nodes: List<ProxmoxNode>, timestamp: Instant): List<MetricSample> =
        nodes.flatMap { node ->
            buildList {
                node.cpuUsagePercent?.let { add(sample(MetricId.PROXMOX_NODE_CPU_PERCENT, node.name, timestamp, it)) }
                node.memoryUsedBytes?.let {
                    add(sample(MetricId.PROXMOX_NODE_MEMORY_USED_BYTES, node.name, timestamp, it.toDouble()))
                }
            }
        }
}
