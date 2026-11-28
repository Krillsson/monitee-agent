package com.krillsson.sysapi

import com.krillsson.sysapi.util.logger
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.Executor

object Threads {
    const val SAMPLING_SCHEDULER = "samplingTaskScheduler"
    const val IO_SCHEDULER = "ioTaskScheduler"
    const val ASYNC_EXECUTOR = "asyncExecutor"
    const val CHECK_EXECUTOR = "checkExecutor"
    const val FILE_OPERATION_EXECUTOR = "fileOperationExecutor"
    const val CONTAINER_UPDATE_EXECUTOR = "containerUpdateExecutor"
    const val CONTAINER_BATCH_UPDATE_EXECUTOR = "containerBatchUpdateExecutor"
    const val POWER_COMMAND_EXECUTOR = "powerCommandExecutor"

    const val SAMPLING_THREADS = 3
    const val IO_THREADS = 6
    const val CHECK_THREADS = 4
    const val FILE_OPERATION_THREADS = 2
    const val POWER_COMMAND_THREADS = 2
    const val ASYNC_CORE_THREADS = 2
    const val ASYNC_MAX_THREADS = 42
    const val ASYNC_QUEUE_CAPACITY = 11

    const val SAMPLING_AWAIT_SECONDS = 5
    const val IO_AWAIT_SECONDS = 10
    const val CHECK_AWAIT_SECONDS = 5
    const val LONG_JOB_AWAIT_SECONDS = 30
    const val POWER_COMMAND_AWAIT_SECONDS = 10
    const val ASYNC_AWAIT_SECONDS = 10

    fun scheduler(
        threadNamePrefix: String,
        poolSize: Int,
        finishRunningTasks: Boolean,
        awaitTerminationSeconds: Int
    ): ThreadPoolTaskScheduler = ThreadPoolTaskScheduler().apply {
        setThreadNamePrefix(threadNamePrefix)
        setPoolSize(poolSize)
        setWaitForTasksToCompleteOnShutdown(finishRunningTasks)
        setAwaitTerminationSeconds(awaitTerminationSeconds)
    }

    fun executor(
        threadNamePrefix: String,
        threads: Int,
        finishQueuedTasks: Boolean,
        awaitTerminationSeconds: Int
    ): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        setThreadNamePrefix(threadNamePrefix)
        corePoolSize = threads
        maxPoolSize = threads
        setDaemon(true)
        setWaitForTasksToCompleteOnShutdown(finishQueuedTasks)
        setAwaitTerminationSeconds(awaitTerminationSeconds)
    }
}

@Configuration
class SchedulingConfiguration {

    @Bean(Threads.SAMPLING_SCHEDULER, "taskScheduler")
    fun samplingTaskScheduler(): ThreadPoolTaskScheduler = Threads.scheduler(
        "sampling-",
        Threads.SAMPLING_THREADS,
        finishRunningTasks = false,
        awaitTerminationSeconds = Threads.SAMPLING_AWAIT_SECONDS
    )

    @Bean(Threads.IO_SCHEDULER)
    fun ioTaskScheduler(): ThreadPoolTaskScheduler = Threads.scheduler(
        "io-",
        Threads.IO_THREADS,
        finishRunningTasks = true,
        awaitTerminationSeconds = Threads.IO_AWAIT_SECONDS
    )

    @Bean("applicationTaskExecutor")
    @Primary
    fun applicationTaskExecutor(builder: ThreadPoolTaskExecutorBuilder): ThreadPoolTaskExecutor = builder.build()

    @Bean(Threads.ASYNC_EXECUTOR)
    fun asyncExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = Threads.ASYNC_CORE_THREADS
        maxPoolSize = Threads.ASYNC_MAX_THREADS
        queueCapacity = Threads.ASYNC_QUEUE_CAPACITY
        setThreadNamePrefix("@Async-")
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(Threads.ASYNC_AWAIT_SECONDS)
    }

    @Bean(Threads.CHECK_EXECUTOR)
    fun checkExecutor(): ThreadPoolTaskExecutor = Threads.executor(
        "check-runner-",
        Threads.CHECK_THREADS,
        finishQueuedTasks = false,
        awaitTerminationSeconds = Threads.CHECK_AWAIT_SECONDS
    )

    @Bean(Threads.FILE_OPERATION_EXECUTOR)
    fun fileOperationExecutor(): ThreadPoolTaskExecutor = Threads.executor(
        "file-operation-",
        Threads.FILE_OPERATION_THREADS,
        finishQueuedTasks = true,
        awaitTerminationSeconds = Threads.LONG_JOB_AWAIT_SECONDS
    )

    @Bean(Threads.CONTAINER_UPDATE_EXECUTOR)
    fun containerUpdateExecutor(): ThreadPoolTaskExecutor = Threads.executor(
        "container-update-",
        1,
        finishQueuedTasks = true,
        awaitTerminationSeconds = Threads.LONG_JOB_AWAIT_SECONDS
    )

    @Bean(Threads.CONTAINER_BATCH_UPDATE_EXECUTOR)
    fun containerBatchUpdateExecutor(): ThreadPoolTaskExecutor = Threads.executor(
        "container-batch-update-",
        1,
        finishQueuedTasks = true,
        awaitTerminationSeconds = Threads.LONG_JOB_AWAIT_SECONDS
    )

    @Bean(Threads.POWER_COMMAND_EXECUTOR)
    fun powerCommandExecutor(): ThreadPoolTaskExecutor = Threads.executor(
        "power-command-",
        Threads.POWER_COMMAND_THREADS,
        finishQueuedTasks = true,
        awaitTerminationSeconds = Threads.POWER_COMMAND_AWAIT_SECONDS
    )
}

@Configuration
class AsyncExecutionConfiguration(
    @Qualifier(Threads.ASYNC_EXECUTOR) private val asyncExecutor: Executor
) : AsyncConfigurer {

    private val logger by logger()

    override fun getAsyncExecutor(): Executor = asyncExecutor

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler {
        return AsyncUncaughtExceptionHandler { ex, method, params ->
            logger.error("$method with parameters $params encountered an uncaught exception", ex)
        }
    }
}
