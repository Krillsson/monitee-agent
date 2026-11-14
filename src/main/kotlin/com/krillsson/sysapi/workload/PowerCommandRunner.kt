package com.krillsson.sysapi.workload

import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Component
class PowerCommandRunner {
    companion object {
        private const val THREADS = 2
    }

    private val threadCount = AtomicInteger()

    private val executor: ExecutorService = Executors.newFixedThreadPool(THREADS) { task ->
        Thread(task, "power-command-${threadCount.incrementAndGet()}").apply { isDaemon = true }
    }

    fun submit(task: Runnable) {
        executor.execute(task)
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }
}
