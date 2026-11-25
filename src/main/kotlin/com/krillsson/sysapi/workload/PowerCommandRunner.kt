package com.krillsson.sysapi.workload

import com.krillsson.sysapi.Threads
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.Executor

@Component
class PowerCommandRunner(
    @Qualifier(Threads.POWER_COMMAND_EXECUTOR) private val executor: Executor
) {
    fun submit(task: Runnable) {
        executor.execute(task)
    }
}
