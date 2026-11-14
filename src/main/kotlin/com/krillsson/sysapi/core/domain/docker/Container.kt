package com.krillsson.sysapi.core.domain.docker

data class Container(
    val command: String,
    val created: Long,
    val hostConfig: HostConfig,
    val config: Config,
    val id: String,
    val image: String,
    val imageID: String,
    val labels: Map<String, String>,
    val mounts: List<Mount>,
    val names: List<String>,
    val networkSettings: List<NetworkSetting>,
    val ports: List<PortBinding>,
    val sizeRootFs: Long,
    val sizeRw: Long,
    val state: State,
    val health: Health?,
    val status: String
)

fun Container.displayName() = names.firstOrNull()?.trimStart('/')?.takeIf { it.isNotBlank() } ?: id.take(12)
