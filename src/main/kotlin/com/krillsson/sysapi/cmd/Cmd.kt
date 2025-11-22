package com.krillsson.sysapi.cmd

import com.krillsson.sysapi.util.logger
import org.apache.commons.exec.*
import org.apache.commons.io.output.ByteArrayOutputStream
import reactor.core.publisher.Flux
import java.nio.charset.Charset


object Cmd {

    val logger by logger()

    private const val CMD_EXECUTABLE = "cmd.exe" // Rely on PATH resolution.

    fun executeToText(
        command: String,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<String> {
        val commandLine = createCmdCommand(command)
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

    fun executeToExitStatus(
        command: String,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<Int> {
        val commandLine = createCmdCommand(command)
        return try {
            val resultHandler = executeWithWatchdog(timeoutAmountMillis, commandLine)
            Result.success(resultHandler.exitValue)
        } catch (throwable: Throwable) {
            logger.error("Failed to execute ${commandLine.toStrings().joinToString(" ")}", throwable)
            Result.failure(throwable)
        }
    }

    fun checkIfCommandExists(
        command: String,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<Boolean> {
        // `where /q <command>` returns 0 if found, else 1.
        val commandLine = CommandLine(CMD_EXECUTABLE)
        commandLine.addArgument("/C")
        commandLine.addArgument("where /q ${'$'}command")
        return try {
            val resultHandler = executeWithWatchdog(timeoutAmountMillis, commandLine)
            Result.success(resultHandler.exitValue == 0)
        } catch (throwable: Throwable) {
            logger.error("Failed to execute ${commandLine.toStrings().joinToString(" ")}", throwable)
            Result.failure(throwable)
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

    fun executeToTextContinuously(command: String): Flux<String> {
        return Flux.create { emitter ->
            val commandLine = createCmdCommand(command)
            logger.debug("Executing ${commandLine.toStrings().joinToString(" ")}")

            val executionThread = Thread {
                try {
                    val process = ProcessBuilder(commandLine.toStrings().toList())
                        .redirectErrorStream(true)
                        .start()

                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            logger.debug("Result: $$line")
                            emitter.next(line)
                        }
                    }

                    val exitCode = process.waitFor()
                    if (exitCode != 0) {
                        emitter.error(IllegalStateException("Command exited with code $exitCode"))
                    } else {
                        emitter.complete()
                    }
                } catch (e: Exception) {
                    emitter.error(e)
                }
            }
            executionThread.start()

            emitter.onDispose {
                try {
                    executionThread.interrupt()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun createCmdCommand(command: String): CommandLine {
        val commandLine = CommandLine(CMD_EXECUTABLE)
        commandLine.addArgument("/C")
        // Avoid additional quoting; Apache Exec handles argument boundaries.
        commandLine.addArgument(command, false)
        return commandLine
    }
}

