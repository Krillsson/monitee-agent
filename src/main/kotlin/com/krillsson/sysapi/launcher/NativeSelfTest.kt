package com.krillsson.sysapi.launcher

import com.github.dockerjava.api.model.Container
import com.github.dockerjava.core.DockerClientConfig
import oshi.SystemInfo
import java.io.PrintStream
import java.nio.charset.Charset

class NativeSelfTest(private val checks: List<Check>) {

    class Check(val name: String, val run: () -> Unit)

    val names: List<String> get() = checks.map { it.name }

    fun run(out: PrintStream, inNativeImage: Boolean): Int {
        val failures = checks.mapNotNull { check ->
            runCatching(check.run).fold(
                onSuccess = {
                    out.println("ok      ${check.name}")
                    null
                },
                onFailure = { failure ->
                    out.println("FAILED  ${check.name}: $failure")
                    failure.printStackTrace(out)
                    failure
                }
            )
        }
        return when {
            failures.isEmpty() -> 0
            inNativeImage && failures.any { StartupFailure.isNativeLimitation(it) } -> StartupFailure.NATIVE_LIMITATION_EXIT_CODE
            else -> 1
        }
    }

    companion object {
        const val CONTAINER_LIST_JSON = """[{"Id":"4f2a","Names":["/web"],"Image":"nginx:1.27","ImageID":"sha256:9c1b","Command":"nginx","Created":1760000000,"State":"running","Status":"Up 2 hours","Ports":[{"IP":"0.0.0.0","PrivatePort":80,"PublicPort":8080,"Type":"tcp"}],"Labels":{"com.docker.compose.project":"site"},"HostConfig":{"NetworkMode":"bridge"},"NetworkSettings":{"Networks":{"bridge":{"IPAddress":"172.17.0.2"}}},"Mounts":[{"Type":"bind","Source":"/srv/web","Destination":"/usr/share/nginx/html","Mode":"ro","RW":false}]}]"""

        const val DOCKER_NAMED_PIPE_KERNEL32 = "com.github.dockerjava.transport.NamedPipeSocket\$Kernel32"

        fun forThisHost(onWindows: Boolean = System.getProperty("os.name").startsWith("Windows")): NativeSelfTest = NativeSelfTest(
            listOfNotNull(
                Check("charsets") { Charset.forName("Windows-1252") },
                Check("docker models") { readContainerList() },
                Check("docker named pipe") { loadDockerNamedPipeBinding() }.takeIf { onWindows },
                Check("oshi") { readHardware() }
            )
        )

        fun loadDockerNamedPipeBinding() {
            Class.forName(DOCKER_NAMED_PIPE_KERNEL32, true, NativeSelfTest::class.java.classLoader)
        }

        fun readContainerList() {
            val containers = DockerClientConfig.getDefaultObjectMapper()
                .readValue(CONTAINER_LIST_JSON, Array<Container>::class.java)
            val port = containers.single().ports.single()
            check(port.publicPort == 8080) { "Container.ports came back without its fields: $port" }
        }

        private fun readHardware() {
            val systemInfo = SystemInfo()
            systemInfo.hardware.processor.processorIdentifier.name
            systemInfo.hardware.memory.total
            systemInfo.operatingSystem.currentProcess.residentMemory
        }
    }
}
