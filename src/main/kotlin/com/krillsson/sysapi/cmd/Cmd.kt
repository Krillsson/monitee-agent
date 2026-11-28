package com.krillsson.sysapi.cmd

import com.krillsson.sysapi.bash.CommandArgument
import com.krillsson.sysapi.util.logger
import org.apache.commons.exec.*
import org.apache.commons.io.output.ByteArrayOutputStream
import java.nio.charset.Charset


object Cmd {

    val logger by logger()

    fun executeToText(
        arguments: List<String>,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<String> {
        val commandLine = createCommand(arguments)
        return try {
            val watchdog = ExecuteWatchdog(timeoutAmountMillis)
            val resultHandler = DefaultExecuteResultHandler()
            val stdout = ByteArrayOutputStream()
            val psh = PumpStreamHandler(stdout)
            logger.debug("Executing ${commandLine.toStrings().joinToString(" ")}")
            val exec = DefaultExecutor()
            exec.streamHandler = psh
            exec.watchdog = watchdog
            exec.execute(commandLine, resultHandler)
            resultHandler.waitFor()
            val result = stdout.toString(Charset.defaultCharset())
            logger.debug("Result: $result")
            Result.success(result)
        } catch (throwable: Throwable) {
            logger.error("Failed to execute ${commandLine.toStrings().joinToString(" ")}", throwable)
            Result.failure(throwable)
        }
    }

    fun checkIfCommandExists(
        command: String,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<Boolean> {
        return when {
            !CommandArgument.isCommandName(command) -> Result.success(false)
            else -> {
                // `where /q <command>` returns 0 if found, else 1.
                val commandLine = createCommand(listOf("where", "/q", command))
                try {
                    val resultHandler = executeWithWatchdog(timeoutAmountMillis, commandLine)
                    Result.success(resultHandler.exitValue == 0)
                } catch (throwable: Throwable) {
                    logger.error("Failed to execute ${commandLine.toStrings().joinToString(" ")}", throwable)
                    Result.failure(throwable)
                }
            }
        }
    }

    private fun executeWithWatchdog(
        timeoutAmountMillis: Long,
        commandLine: CommandLine
    ): DefaultExecuteResultHandler {
        val watchdog = ExecuteWatchdog(timeoutAmountMillis)
        val resultHandler = DefaultExecuteResultHandler()
        logger.debug("Executing ${commandLine.toStrings().joinToString(" ")}")
        val exec = DefaultExecutor()
        exec.watchdog = watchdog
        exec.execute(commandLine, resultHandler)
        resultHandler.waitFor()
        return resultHandler
    }

    private fun createCommand(arguments: List<String>): CommandLine {
        val commandLine = CommandLine(arguments.first())
        arguments.drop(1).forEach { commandLine.addArgument(it, false) }
        return commandLine
    }
}
