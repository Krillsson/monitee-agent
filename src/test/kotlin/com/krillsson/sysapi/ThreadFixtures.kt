package com.krillsson.sysapi

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

fun testExecutor(prefix: String, threads: Int, finishQueuedTasks: Boolean = true, awaitSeconds: Int = 5): ThreadPoolTaskExecutor =
    Threads.executor(prefix, threads, finishQueuedTasks, awaitSeconds).apply { initialize() }

fun runnerExecutor(): ThreadPoolTaskExecutor = testExecutor("check-runner-", Threads.CHECK_THREADS, finishQueuedTasks = false)

fun ThreadPoolTaskExecutor.shutdownInBackground(): Thread {
    val shutdown = Thread { shutdown() }.apply { isDaemon = true }
    shutdown.start()
    val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
    while (!threadPoolExecutor.isShutdown && System.nanoTime() < deadline) {
        Thread.sleep(5)
    }
    return shutdown
}

fun ThreadPoolTaskExecutor.initialized(): ThreadPoolTaskExecutor = apply { initialize() }
