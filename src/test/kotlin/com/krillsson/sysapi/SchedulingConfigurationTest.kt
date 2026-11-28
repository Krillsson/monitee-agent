package com.krillsson.sysapi

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SchedulingConfigurationTest {

    private val configuration = SchedulingConfiguration()

    @Test
    fun `runs sampling tasks and I-O tasks on differently named threads`() {
        // Given
        val sampling = configuration.samplingTaskScheduler().apply { initialize() }
        val io = configuration.ioTaskScheduler().apply { initialize() }
        val samplingThread = AtomicReference<String>()
        val ioThread = AtomicReference<String>()
        val done = CountDownLatch(2)

        // When
        sampling.execute { samplingThread.set(Thread.currentThread().name); done.countDown() }
        io.execute { ioThread.set(Thread.currentThread().name); done.countDown() }
        done.await(5, TimeUnit.SECONDS)

        // Then
        samplingThread.get().startsWith("sampling-") shouldBe true
        ioThread.get().startsWith("io-") shouldBe true
        sampling.shutdown()
        io.shutdown()
    }

    @Test
    fun `keeps a blocked I-O thread from delaying sampling`() {
        // Given
        val sampling = configuration.samplingTaskScheduler().apply { initialize() }
        val io = configuration.ioTaskScheduler().apply { initialize() }
        val release = CountDownLatch(1)
        repeat(Threads.IO_THREADS) { io.execute { release.await(5, TimeUnit.SECONDS) } }
        val sampled = CountDownLatch(1)

        // When
        sampling.execute { sampled.countDown() }

        // Then
        sampled.await(2, TimeUnit.SECONDS) shouldBe true
        release.countDown()
        sampling.shutdown()
        io.shutdown()
    }

    @Test
    fun `lets a running I-O task finish when the I-O scheduler is shut down`() {
        // Given
        val io = configuration.ioTaskScheduler().apply { initialize() }
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean()
        val completed = AtomicBoolean()
        io.execute {
            started.countDown()
            try {
                release.await(5, TimeUnit.SECONDS)
                completed.set(true)
            } catch (e: InterruptedException) {
                interrupted.set(true)
            }
        }
        started.await(5, TimeUnit.SECONDS)

        // When
        val shutdown = Thread { io.shutdown() }.apply { start() }
        Thread.sleep(200)
        release.countDown()
        shutdown.join(5000)

        // Then
        completed.get() shouldBe true
        interrupted.get() shouldBe false
    }

    @Test
    fun `interrupts a running sampling task when the sampling scheduler is shut down`() {
        // Given
        val sampling = configuration.samplingTaskScheduler().apply { initialize() }
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        sampling.execute {
            started.countDown()
            try {
                Thread.sleep(10_000)
            } catch (e: InterruptedException) {
                interrupted.countDown()
            }
        }
        started.await(5, TimeUnit.SECONDS)

        // When
        sampling.shutdown()

        // Then
        interrupted.await(5, TimeUnit.SECONDS) shouldBe true
    }

    @ParameterizedTest
    @CsvSource(
        "check-runner-,4",
        "file-operation-,2",
        "container-update-,1",
        "container-batch-update-,1",
        "power-command-,2"
    )
    fun `keeps each job pool at its fixed size with daemon threads under its own name`(prefix: String, size: Int) {
        // Given
        val executor = Threads.executor(prefix, size, finishQueuedTasks = true, awaitTerminationSeconds = 1).initialized()
        val names = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val daemons = AtomicInteger()
        val allBusy = CountDownLatch(size)
        val release = CountDownLatch(1)

        // When
        repeat(size * 2) {
            executor.execute {
                names.add(Thread.currentThread().name)
                if (Thread.currentThread().isDaemon) daemons.incrementAndGet()
                allBusy.countDown()
                release.await(5, TimeUnit.SECONDS)
            }
        }
        allBusy.await(5, TimeUnit.SECONDS)
        Thread.sleep(100)

        // Then
        executor.threadPoolExecutor.poolSize shouldBe size
        names.all { it.startsWith(prefix) } shouldBe true
        daemons.get() shouldBe size
        release.countDown()
        executor.shutdown()
    }

    @Test
    fun `keeps the async executor sizes it always had`() {
        // When
        val executor = configuration.asyncExecutor()

        // Then
        executor.corePoolSize shouldBe 2
        executor.maxPoolSize shouldBe 42
        executor.threadNamePrefix shouldBe "@Async-"
    }
}
