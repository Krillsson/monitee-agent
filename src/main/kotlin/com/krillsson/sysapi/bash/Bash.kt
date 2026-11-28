package com.krillsson.sysapi.bash

import com.krillsson.sysapi.util.logger
import org.apache.commons.exec.*
import org.apache.commons.io.output.ByteArrayOutputStream
import reactor.core.publisher.Flux
import java.io.File
import java.nio.charset.Charset

object Bash {

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

    fun executeToExitStatus(
        arguments: List<String>,
        timeoutAmountMillis: Long = 3 * 1000
    ): Result<Int> {
        val commandLine = createCommand(arguments)
        return try {
            val resultHandler = executeWithWatchdog(timeoutAmountMillis, commandLine)
            Result.success(resultHandler.exitValue)
        } catch (throwable: Throwable) {
            logger.error("Failed to execute ${commandLine.toStrings().joinToString(" ")}", throwable)
            Result.failure(throwable)
        }
    }

    fun checkIfCommandExists(command: String): Result<Boolean> {
        return try {
            Result.success(findOnPath(command, System.getenv("PATH").orEmpty()) != null)
        } catch (throwable: Throwable) {
            logger.error("Failed to look up $command", throwable)
            Result.failure(throwable)
        }
    }

    fun findOnPath(command: String, path: String): File? {
        return when {
            !CommandArgument.isCommandName(command) -> null
            else -> path
                .split(File.pathSeparator)
                .filter { it.isNotBlank() }
                .map { File(it, command) }
                .firstOrNull { it.isFile && it.canExecute() }
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

    fun executeToTextContinuously(arguments: List<String>): Flux<String> {
        return Flux.create { emitter ->
            val commandLine = createCommand(arguments)
            logger.debug("Executing ${commandLine.toStrings().joinToString(" ")}")

            // Run the command in a separate thread
            val executionThread = Thread {
                try {
                    val process = ProcessBuilder(commandLine.toStrings().toList())
                        .redirectErrorStream(true) // Combine stdout and stderr
                        .start()

                    // Read the process's output stream
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            logger.debug("Result: $line")
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

            // Cleanup on disposal
            emitter.onDispose {
                try {
                    executionThread.interrupt()
                } catch (e: Exception) {
                    // Suppress cleanup exceptions
                }
            }
        }
    }

    private fun createCommand(arguments: List<String>): CommandLine {
        val commandLine = CommandLine(arguments.first())
        arguments.drop(1).forEach { commandLine.addArgument(it, false) }
        return commandLine
    }
}
