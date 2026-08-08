package com.krillsson.sysapi.docker

import com.krillsson.sysapi.util.EnvironmentUtils
import com.krillsson.sysapi.util.logger
import org.springframework.stereotype.Component
import java.io.File

@Component
class SelfContainer {
    companion object {
        private const val ID_PREFIX_LENGTH = 12
        private val CONTAINER_ID = Regex("[0-9a-f]{64}")
        private val SHORT_CONTAINER_ID = Regex("^[0-9a-f]{12}$")
        private val CONTAINER_FILE_MOUNTS = setOf("/etc/hostname", "/etc/hosts", "/etc/resolv.conf")
    }

    private val logger by logger()

    private val id: String? by lazy { resolveId() }

    fun isSelf(containerId: String): Boolean {
        val self = id ?: return false
        return containerId.take(ID_PREFIX_LENGTH) == self.take(ID_PREFIX_LENGTH)
    }

    private fun resolveId(): String? {
        val id = fromMountInfo() ?: fromCgroup() ?: fromHostName()
        if (id == null) {
            logger.debug("Could not determine which container this process runs in, if any")
        } else {
            logger.info("Running inside container {}", id)
        }
        return id
    }

    // The daemon bind mounts /etc/hostname and its siblings from /var/lib/docker/containers/<id>,
    // and the source path of those mounts is the only place naming our own id that survives
    // cgroup v2, which no longer puts the id in /proc/self/cgroup.
    private fun fromMountInfo() = readLines("/proc/self/mountinfo")
        .firstNotNullOfOrNull { line ->
            val fields = line.split(' ')
            if (fields.getOrNull(4) in CONTAINER_FILE_MOUNTS) {
                CONTAINER_ID.find(fields.getOrNull(3).orEmpty())?.value
            } else {
                null
            }
        }

    private fun fromCgroup() = readLines("/proc/self/cgroup")
        .firstNotNullOfOrNull { CONTAINER_ID.find(it)?.value }

    private fun fromHostName() = EnvironmentUtils.hostName.takeIf { SHORT_CONTAINER_ID.matches(it) }

    private fun readLines(path: String): List<String> {
        val file = File(path)
        return if (file.canRead()) {
            runCatching { file.readLines() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
}
