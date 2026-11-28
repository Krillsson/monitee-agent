package com.krillsson.sysapi.proxmox

import com.krillsson.sysapi.graphql.domain.ProxmoxInfoAvailable
import com.krillsson.sysapi.graphql.domain.ProxmoxInfoUnavailable
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@RegisterReflectionForBinding(
    ProxmoxInfoAvailable::class,
    ProxmoxInfoUnavailable::class,
    ProxmoxCluster::class,
    ProxmoxNode::class,
    ProxmoxNodeStatus::class,
    ProxmoxGuest::class,
    ProxmoxGuestKind::class,
    ProxmoxGuestStatus::class,
    ProxmoxGuestMetrics::class,
    ProxmoxGuestCpuUsage::class,
    ProxmoxGuestMemoryUsage::class,
    ProxmoxGuestNetworkUsage::class,
    ProxmoxGuestBlockIOUsage::class,
    ProxmoxGuestDiskUsage::class,
    ProxmoxGuestNetwork::class,
    ProxmoxGuestNetworkInterface::class,
    ProxmoxStorage::class,
    ProxmoxStorageStatus::class,
    ProxmoxApi::class,
    ProxmoxApi.Version::class,
    ProxmoxApi.Resource::class,
    ProxmoxApi.ClusterStatusEntry::class,
    ProxmoxApi.NodeStatus::class,
    ProxmoxApi.NodeStatus.CpuInfo::class,
    ProxmoxApi.NodeStatus.Ksm::class,
    ProxmoxApi.NodeStatus.RootFs::class,
    ProxmoxApi.NodeStatus.CurrentKernel::class,
    ProxmoxApi.GuestStatus::class,
    ProxmoxApi.GuestConfig::class,
    ProxmoxApi.AgentInterfaces::class,
    ProxmoxApi.AgentInterface::class,
    ProxmoxApi.AgentHostName::class,
    ProxmoxApi.AgentHostName.HostName::class,
    ProxmoxApi.LxcInterface::class,
    ProxmoxApi.IpAddress::class,
    ProxmoxApi.TaskStatus::class,
    ProxmoxApi.TaskLogLine::class,
)
class ProxmoxNativeHints
