package com.krillsson.sysapi.diagnostics

import com.krillsson.sysapi.util.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.io.File
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory
import java.time.Duration
import javax.management.ObjectName

@Service
@ConditionalOnProperty(value = ["diagnostics.memory.enabled"], havingValue = "true", matchIfMissing = true)
class MemoryDiagnostics(
    @Value("\${diagnostics.memory.deepReportEvery:6}") private val deepReportEvery: Int,
    @Value("\${diagnostics.memory.histogramLines:45}") private val histogramLines: Int,
    @Value("\${diagnostics.memory.mappingLines:20}") private val mappingLines: Int
) {
    private val logger by logger()

    private val mbeanServer by lazy { runCatching { ManagementFactory.getPlatformMBeanServer() }.getOrNull() }
    private val diagnosticCommandName by lazy {
        runCatching { ObjectName("com.sun.management:type=DiagnosticCommand") }.getOrNull()
    }
    private val mappingHeader = Regex("^[0-9a-f]+-[0-9a-f]+ ")
    private val threadIndex = Regex("[-_#]?\\d+$")

    private var reportCount = 0
    private var nmtBaselined = false

    @Scheduled(
        initialDelayString = "\${diagnostics.memory.initialDelayMs:120000}",
        fixedRateString = "\${diagnostics.memory.intervalMs:600000}"
    )
    fun report() {
        try {
            logger.info(buildReport())
        } catch (e: Exception) {
            logger.warn("Memory diagnostics failed", e)
        }
    }

    private fun buildReport(): String {
        reportCount++
        val deep = reportCount == 1 || reportCount % deepReportEvery == 0
        val summary = diagnosticCommand("vmNativeMemory", "summary")
        return buildString {
            appendLine()
            appendLine("======== memory diagnostics #$reportCount${if (deep) " (deep)" else ""} ========")
            append(headlineSection(summary))
            append(runtimeSection())
            append(processSection())
            append(controlGroupSection())
            append(javaMemorySection())
            append(threadSection())
            append(nativeMemorySection(deep, summary))
            if (deep) {
                append(deepSection())
            }
            appendLine("======== end of memory diagnostics #$reportCount ========")
        }
    }

    private fun headlineSection(summary: String) = buildString {
        val resident = procStatusBytes("VmRSS")
        val committed = Regex("committed=(\\d+)KB")
            .find(summary.substringAfter("Total:", ""))
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.times(1024)
        val heap = ManagementFactory.getMemoryMXBean().heapMemoryUsage
        val pools = ManagementFactory.getMemoryPoolMXBeans().associate { it.name to it.usage.committed }
        appendLine("-- headline --")
        appendLine("resident set: ${format(resident)} (peak ${format(procStatusBytes("VmHWM"))})")
        appendLine("cgroup usage: ${controlGroupUsage()?.let { format(it) } ?: "unavailable"}")
        appendLine("nmt committed: ${committed?.let { format(it) } ?: "unavailable"}")
        appendLine(
            "untracked native: ${
                if (committed != null && resident > 0) format(resident - committed) else "unavailable"
            }"
        )
        appendLine("heap: ${format(heap.used)} used of ${format(heap.committed)} committed")
        appendLine("metaspace committed: ${format(pools["Metaspace"] ?: 0)}")
        appendLine("compressed class space committed: ${format(pools["Compressed Class Space"] ?: 0)}")
        appendLine("code cache committed: ${format(pools.filterKeys { it.contains("CodeCache") || it.startsWith("CodeHeap") }.values.sum())}")
        appendLine("classes loaded: ${ManagementFactory.getClassLoadingMXBean().loadedClassCount}")
    }

    private fun runtimeSection() = buildString {
        val runtime = ManagementFactory.getRuntimeMXBean()
        appendLine("-- runtime --")
        appendLine("pid: ${ProcessHandle.current().pid()}")
        appendLine("uptime: ${Duration.ofMillis(runtime.uptime)}")
        appendLine("jvm: ${runtime.vmName} ${runtime.vmVersion} (${System.getProperty("java.vendor")})")
        appendLine("processors: ${Runtime.getRuntime().availableProcessors()}")
        appendLine("jvm arguments: ${runtime.inputArguments.joinToString(" ")}")
        listOf("LD_PRELOAD", "MALLOC_CONF", "MALLOC_ARENA_MAX", "MALLOC_TRIM_THRESHOLD_", "JAVA_TOOL_OPTIONS")
            .forEach { appendLine("env $it: ${System.getenv(it) ?: "<unset>"}") }
        System.getenv()
            .filterKeys { it.startsWith("MALLOC") || it.startsWith("LD_") || it.startsWith("JEMALLOC") }
            .forEach { (key, value) -> appendLine("env (allocator) $key: $value") }
        appendLine("jemalloc mapped: ${mappedPaths("jemalloc").ifEmpty { listOf("no") }.joinToString(" ")}")
        appendLine("shared archives mapped: ${(mappedPaths(".jsa") + mappedPaths(".aot")).ifEmpty { listOf("none") }.joinToString(" ")}")
    }

    private fun processSection() = buildString {
        appendLine("-- process --")
        appendLine(
            readKeys(
                File("/proc/self/status"),
                listOf("VmSize", "VmRSS", "VmHWM", "RssAnon", "RssFile", "RssShmem", "VmSwap", "Threads")
            )
        )
        appendLine(
            readKeys(
                File("/proc/self/smaps_rollup"),
                listOf("Rss", "Pss", "Shared_Clean", "Shared_Dirty", "Private_Clean", "Private_Dirty", "Swap")
            )
        )
    }

    private fun controlGroupSection() = buildString {
        appendLine("-- control group --")
        appendLine("/proc/self/cgroup: ${File("/proc/self/cgroup").let { if (it.canRead()) it.readText().trim().replace("\n", " | ") else "unavailable" }}")
        val version2 = controlGroupDirectory("", "memory.current")
        if (version2 != null) {
            appendLine("cgroup: v2 at ${version2.path}")
            listOf("memory.current", "memory.peak", "memory.max", "memory.high", "memory.swap.current").forEach {
                appendLine("$it: ${readValue(File(version2, it))}")
            }
            appendLine(
                readKeys(
                    File(version2, "memory.stat"),
                    listOf("anon", "file", "kernel", "kernel_stack", "slab", "sock", "shmem", "file_mapped"),
                    " "
                )
            )
            return@buildString
        }
        val version1 = controlGroupDirectory("memory", "memory.usage_in_bytes")
        if (version1 == null) {
            appendLine("no cgroup memory controller visible")
            return@buildString
        }
        appendLine("cgroup: v1 at ${version1.path}")
        listOf("memory.usage_in_bytes", "memory.max_usage_in_bytes", "memory.limit_in_bytes").forEach {
            appendLine("$it: ${readValue(File(version1, it))}")
        }
        appendLine(
            readKeys(
                File(version1, "memory.stat"),
                listOf("rss", "cache", "mapped_file", "swap", "total_rss", "total_cache", "total_mapped_file"),
                " "
            )
        )
    }

    private fun controlGroupDirectory(controller: String, probe: String): File? {
        val mount = File("/sys/fs/cgroup", controller)
        val relative = File("/proc/self/cgroup")
            .takeIf { it.canRead() }
            ?.readLines()
            ?.firstOrNull { it.split(":").getOrNull(1).orEmpty() == controller }
            ?.substringAfterLast(":")
            .orEmpty()
        return listOf(File(mount, relative), mount).firstOrNull { File(it, probe).canRead() }
    }

    private fun javaMemorySection() = buildString {
        appendLine("-- java memory --")
        val memory = ManagementFactory.getMemoryMXBean()
        appendLine("heap: ${format(memory.heapMemoryUsage.used)} used, ${format(memory.heapMemoryUsage.committed)} committed, ${format(memory.heapMemoryUsage.max)} max")
        appendLine("non heap: ${format(memory.nonHeapMemoryUsage.used)} used, ${format(memory.nonHeapMemoryUsage.committed)} committed")
        ManagementFactory.getMemoryPoolMXBeans().forEach {
            appendLine("pool ${it.name}: ${format(it.usage.used)} used, ${format(it.usage.committed)} committed, ${format(it.usage.max)} max")
        }
        ManagementFactory.getGarbageCollectorMXBeans().forEach {
            appendLine("gc ${it.name}: ${it.collectionCount} collections, ${it.collectionTime} ms")
        }
        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java).forEach {
            appendLine("buffer pool ${it.name}: ${it.count} buffers, ${format(it.memoryUsed)} used, ${format(it.totalCapacity)} capacity")
        }
        val classes = ManagementFactory.getClassLoadingMXBean()
        appendLine("classes: ${classes.loadedClassCount} loaded, ${classes.totalLoadedClassCount} total, ${classes.unloadedClassCount} unloaded")
        listOf("data/database.sqlite", "data/database.sqlite-wal", "data/database.sqlite-shm").forEach {
            val file = File(it)
            if (file.exists()) {
                appendLine("$it: ${format(file.length())}")
            }
        }
    }

    private fun threadSection() = buildString {
        appendLine("-- threads --")
        val threads = ManagementFactory.getThreadMXBean()
        appendLine("count: ${threads.threadCount}, daemon: ${threads.daemonThreadCount}, peak: ${threads.peakThreadCount}, started: ${threads.totalStartedThreadCount}")
        threads.dumpAllThreads(false, false)
            .groupingBy { threadIndex.replace(it.threadName, "") }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(15)
            .forEach { appendLine("${it.value}\t${it.key}") }
        appendLine("native thread names (includes threads the jvm does not own):")
        append(nativeThreadNames())
    }

    private fun nativeThreadNames(): String {
        val tasks = File("/proc/self/task").listFiles()
            ?: return "/proc/self/task unavailable\n"
        return tasks.mapNotNull { task ->
            File(task, "comm").takeIf { it.canRead() }?.runCatching { readText().trim() }?.getOrNull()
        }
            .groupingBy { threadIndex.replace(it, "") }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(20)
            .joinToString("\n") { "${it.value}\t${it.key}" } + "\n"
    }

    private fun nativeMemorySection(deep: Boolean, summary: String) = buildString {
        appendLine("-- native memory tracking --")
        if (!nmtBaselined) {
            append(summary)
            append(diagnosticCommand("vmNativeMemory", "baseline"))
            nmtBaselined = true
        } else {
            if (deep) {
                append(summary)
            }
            append(diagnosticCommand("vmNativeMemory", "summary.diff"))
        }
    }

    private fun deepSection() = buildString {
        appendLine("-- metaspace --")
        append(diagnosticCommand("vmMetaspace", "basic").substringBefore("Internal statistics").trim())
        appendLine()
        appendLine("-- class loaders by type --")
        append(classLoadersByType())
        appendLine("-- class histogram (forces a full gc, so counts are live objects) --")
        append(diagnosticCommand("gcClassHistogram").lineSequence().take(histogramLines).joinToString("\n"))
        appendLine()
        appendLine("-- resident set by mapping --")
        append(mappingsByResidentSize())
    }

    private fun classLoadersByType(): String {
        val loaders = LinkedHashMap<String, IntArray>()
        val chunks = LinkedHashMap<String, Long>()
        diagnosticCommand("vmClassloaderStats").lineSequence().forEach { line ->
            val columns = line.trim().split(Regex("\\s+"))
            if (columns.size >= 7 && columns[0].startsWith("0x")) {
                val type = columns.drop(6).joinToString(" ")
                loaders.getOrPut(type) { IntArray(2) }.let {
                    it[0]++
                    it[1] += columns[3].toIntOrNull() ?: 0
                }
                chunks.merge(type, columns[4].toLongOrNull() ?: 0L, Long::plus)
            }
        }
        return loaders.entries
            .sortedByDescending { chunks[it.key] ?: 0L }
            .joinToString("\n") { "${format(chunks[it.key] ?: 0L)}\t${it.value[1]} classes\t${it.value[0]} loaders\t${it.key}" } + "\n"
    }

    private fun mappingsByResidentSize(): String {
        val smaps = File("/proc/self/smaps")
        if (!smaps.canRead()) {
            return "/proc/self/smaps unavailable\n"
        }
        val totals = LinkedHashMap<String, Long>()
        val counts = LinkedHashMap<String, Int>()
        var mapping = "[anonymous]"
        smaps.forEachLine { line ->
            when {
                mappingHeader.containsMatchIn(line) -> {
                    val path = line.split(" ", limit = 6).getOrNull(5)?.trim().orEmpty()
                    mapping = if (path.isEmpty()) "[anonymous]" else path
                    counts.merge(mapping, 1, Int::plus)
                }

                line.startsWith("Rss:") -> totals.merge(mapping, kilobytes(line) * 1024, Long::plus)
            }
        }
        return totals.entries
            .sortedByDescending { it.value }
            .take(mappingLines)
            .joinToString("\n") { "${format(it.value)}\t${counts[it.key]} mappings\t${it.key}" } +
                "\n${format(totals.values.sum())}\ttotal resident over ${counts.values.sum()} mappings\n"
    }

    private fun diagnosticCommand(operation: String, vararg arguments: String): String = try {
        val server = requireNotNull(mbeanServer) { "no platform mbean server" }
        val name = requireNotNull(diagnosticCommandName) { "no diagnostic command mbean" }
        server.invoke(
            name,
            operation,
            arrayOf<Any>(arrayOf(*arguments)),
            arrayOf(Array<String>::class.java.name)
        ) as String
    } catch (e: Exception) {
        "$operation unavailable: ${e.javaClass.simpleName}: ${e.message}\n"
    }

    private fun mappedPaths(needle: String): List<String> {
        val maps = File("/proc/self/maps")
        if (!maps.canRead()) {
            return emptyList()
        }
        return maps.useLines { lines ->
            lines.filter { it.contains(needle) }
                .mapNotNull { it.split(" ").lastOrNull()?.trim() }
                .distinct()
                .toList()
        }
    }

    private fun readKeys(file: File, keys: List<String>, separator: String = ":"): String {
        if (!file.canRead()) {
            return "${file.path} unavailable"
        }
        val wanted = keys.toSet()
        return file.readLines()
            .filter { it.substringBefore(separator).trim() in wanted }
            .joinToString("\n") { it.replace(Regex("\\s+"), " ").trim() }
    }

    private fun readValue(file: File) = if (file.canRead()) file.readText().trim() else "unavailable"

    private fun procStatusBytes(key: String): Long {
        val status = File("/proc/self/status")
        if (!status.canRead()) {
            return 0L
        }
        return status.readLines()
            .firstOrNull { it.startsWith("$key:") }
            ?.let { kilobytes(it) * 1024 }
            ?: 0L
    }

    private fun controlGroupUsage(): Long? {
        controlGroupDirectory("", "memory.current")?.let {
            return readValue(File(it, "memory.current")).toLongOrNull()
        }
        controlGroupDirectory("memory", "memory.usage_in_bytes")?.let {
            return readValue(File(it, "memory.usage_in_bytes")).toLongOrNull()
        }
        return null
    }

    private fun kilobytes(line: String) = line.filter { it.isDigit() }.toLongOrNull() ?: 0L

    private fun format(bytes: Long) =
        if (bytes < 0) "unbounded" else "${"%.1f".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0)} MB"
}
