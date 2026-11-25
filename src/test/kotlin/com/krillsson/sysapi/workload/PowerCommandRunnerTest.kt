package com.krillsson.sysapi.workload

import com.krillsson.sysapi.SchedulingConfiguration
import com.krillsson.sysapi.initialized
import com.krillsson.sysapi.shutdownInBackground
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PowerCommandRunnerTest {

    private val executor = SchedulingConfiguration().powerCommandExecutor().initialized()
    private val runner = PowerCommandRunner(executor)

    @Test
    fun `runs a submitted command on a power-command thread, not the caller's`() {
        // Given
        val threadName = java.util.concurrent.atomic.AtomicReference<String>()
        val done = CountDownLatch(1)

        // When
        runner.submit {
            threadName.set(Thread.currentThread().name)
            done.countDown()
        }
        done.await(5, TimeUnit.SECONDS)

        // Then
        threadName.get().startsWith("power-command-") shouldBe true
        executor.shutdown()
    }

    @Test
    fun `finishes the commands running and queued when it is shut down`() {
        // Given
        val bothRunning = CountDownLatch(2)
        val release = CountDownLatch(1)
        val finished = AtomicInteger()
        repeat(3) {
            runner.submit {
                bothRunning.countDown()
                release.await(5, TimeUnit.SECONDS)
                finished.incrementAndGet()
            }
        }
        bothRunning.await(5, TimeUnit.SECONDS)

        // When
        val shutdown = executor.shutdownInBackground()
        release.countDown()
        shutdown.join(5000)

        // Then
        shutdown.isAlive shouldBe false
        finished.get() shouldBe 3
    }
}
