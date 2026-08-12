package com.krillsson.sysapi.logaccess.file

import com.krillsson.sysapi.config.FileBrowserConfiguration
import com.krillsson.sysapi.filebrowser.FileBrowserSandbox
import com.krillsson.sysapi.filebrowser.FileTypeRegistry
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class LogFileAccessAuthorizerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var exposedLog: Path
    private lateinit var browserRoot: Path
    private lateinit var outside: Path
    private val logFilesManager = mockk<LogFilesManager>()

    @BeforeEach
    fun setUp() {
        exposedLog = tempDir.resolve("agent.log")
        exposedLog.writeText("exposed")
        browserRoot = Files.createDirectory(tempDir.resolve("storage"))
        browserRoot.resolve("nginx.log").writeText("browsed")
        outside = tempDir.resolve("secret.txt")
        outside.writeText("secret")
        every { logFilesManager.files() } returns listOf(LogFileReader(exposedLog.toFile()))
    }

    private fun authorizer(
        browserEnabled: Boolean = true,
        maxLogViewBytes: Long = 1024
    ): LogFileAccessAuthorizer {
        val configuration = FileBrowserConfiguration(
            enabled = browserEnabled,
            roots = listOf(browserRoot.toString()),
            maxLogViewBytes = maxLogViewBytes
        )
        return LogFileAccessAuthorizer(
            logFilesManager,
            FileBrowserSandbox(configuration),
            configuration,
            FileTypeRegistry()
        )
    }

    @Test
    fun `allows a file the log reader configuration exposes`() {
        // Given
        val authorizer = authorizer()

        // When
        val file = authorizer.authorizedFileOrNull(exposedLog.toString())

        // Then
        file.shouldNotBeNull().toPath() shouldBe exposedLog.toRealPath()
    }

    @Test
    fun `allows a file inside a file browser root`() {
        // Given
        val authorizer = authorizer()

        // When
        val file = authorizer.authorizedFileOrNull(browserRoot.resolve("nginx.log").toString())

        // Then
        file.shouldNotBeNull().toPath() shouldBe browserRoot.toRealPath().resolve("nginx.log")
    }

    @Test
    fun `denies anything the configuration does not expose`() {
        // Given
        val authorizer = authorizer()

        // When / Then
        authorizer.authorizedFileOrNull(outside.toString()) shouldBe null
        shouldThrow<LogFileAccessDeniedException> { authorizer.authorize(outside.toString()) }
    }

    @Test
    fun `denies a path that traverses out of a browser root`() {
        // Given
        val authorizer = authorizer()

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve("../secret.txt").toString()) shouldBe null
    }

    @Test
    fun `denies a symbolic link inside a browser root`() {
        // Given
        Files.createSymbolicLink(browserRoot.resolve("shortcut.log"), outside)
        val authorizer = authorizer()

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve("shortcut.log").toString()) shouldBe null
    }

    @Test
    fun `denies a browsed file larger than the log viewing limit`() {
        // Given
        browserRoot.resolve("huge.log").writeText("x".repeat(2048))
        val authorizer = authorizer(maxLogViewBytes = 1024)

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve("huge.log").toString()) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(strings = ["notes.html", "archive.zip", "database.sqlite"])
    fun `denies a browsed file the browser does not offer to open as a log`(name: String) {
        // Given
        browserRoot.resolve(name).writeText("contents")
        val authorizer = authorizer()

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve(name).toString()) shouldBe null
    }

    @ParameterizedTest
    @ValueSource(strings = ["nginx.log", "notes.txt", "stdout.out", "messages"])
    fun `allows a browsed file that reads like a log`(name: String) {
        // Given
        browserRoot.resolve(name).writeText("contents")
        val authorizer = authorizer()

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve(name).toString()).shouldNotBeNull()
    }

    @Test
    fun `denies browsed files while the file browser is off`() {
        // Given
        val authorizer = authorizer(browserEnabled = false)

        // When / Then
        authorizer.authorizedFileOrNull(browserRoot.resolve("nginx.log").toString()) shouldBe null
        authorizer.authorizedFileOrNull(exposedLog.toString()).shouldNotBeNull()
    }
}
