package com.krillsson.sysapi.docker

import com.krillsson.sysapi.core.domain.docker.Config
import com.krillsson.sysapi.core.domain.docker.Container
import com.krillsson.sysapi.core.domain.docker.HostConfig
import com.krillsson.sysapi.core.domain.docker.State

fun container(
    id: String,
    name: String,
    labels: Map<String, String> = emptyMap(),
    networkMode: String = "bridge",
    image: String = "nginx:1.25"
): Container = Container(
    command = "nginx",
    created = 0L,
    hostConfig = HostConfig(networkMode = networkMode),
    config = Config(env = emptyList(), volumeBindings = emptyList(), cmd = emptyList(), exposedPorts = emptyList()),
    id = id,
    image = image,
    imageID = "sha256:$id",
    labels = labels,
    mounts = emptyList(),
    names = listOf("/$name"),
    networkSettings = emptyList(),
    ports = emptyList(),
    sizeRootFs = 0L,
    sizeRw = 0L,
    state = State.RUNNING,
    health = null,
    status = "Up"
)
