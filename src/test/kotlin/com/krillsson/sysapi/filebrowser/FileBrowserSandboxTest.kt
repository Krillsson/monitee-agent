package com.krillsson.sysapi.filebrowser

import com.krillsson.sysapi.config.FileBrowserAccess
import com.krillsson.sysapi.config.FileBrowserConfiguration
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Path

class FileBrowserSandboxTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var root: Path
    private lateinit var outside: Path

    @BeforeEach
    fun setUp() {
        root = Files.createDirectory(tempDir.resolve("storage"))
        outside = Files.createDirectory(tempDir.resolve("outside"))
        Files.createDirectory(tempDir.resolve("storage-evil"))
        Files.createFile(root.resolve("readme.txt"))
        Files.createFile(outside.resolve("secret.txt"))
        Files.createDirectory(root.resolve("media"))
    }

    private fun sandbox(
        enabled: Boolean = true,
        access: FileBrowserAccess = FileBrowserAccess.READ_WRITE,
        roots: List<Path> = listOf(root)
    ) = FileBrowserSandbox(
        FileBrowserConfiguration(
            enabled = enabled,
            access = access,
            roots = roots.map { it.toString() }
        )
    )

    @Test
    fun `resolves a file inside a root`() {
        // Given
        val sandbox = sandbox()

        // When
        val resolved = sandbox.resolveExisting(root.resolve("readme.txt").toString())

        // Then
        resolved shouldBe root.toRealPath().resolve("readme.txt")
    }

    @Test
    fun `resolves the root itself`() {
        // Given
        val sandbox = sandbox()

        // When
        val resolved = sandbox.resolveExisting(root.toString())

        // Then
        sandbox.isRoot(resolved) shouldBe true
    }

    @Test
    fun `rejects a path that traverses out of the root`() {
        // Given
        val sandbox = sandbox()
        val traversal = root.resolve("../outside/secret.txt").toString()

        // When
        val thrown = shouldThrow<FileBrowserException> { sandbox.resolveExisting(traversal) }

        // Then
        thrown.message shouldBe "$traversal is outside of the directories this agent exposes"
    }

    @Test
    fun `rejects a path in a directory whose name only starts with the root`() {
        // Given
        val sandbox = sandbox()
        val sibling = tempDir.resolve("storage-evil").resolve("secret.txt")
        Files.createFile(sibling)

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(sibling.toString()) }
    }

    @Test
    fun `rejects a symlink inside the root that points out of it`() {
        // Given
        val link = root.resolve("escape")
        Files.createSymbolicLink(link, outside.resolve("secret.txt"))
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(link.toString()) }
    }

    @Test
    fun `rejects a path that reaches out of the root through a symlinked directory`() {
        // Given
        val link = root.resolve("elsewhere")
        Files.createSymbolicLink(link, outside)
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(link.resolve("secret.txt").toString()) }
    }

    @Test
    fun `rejects a symlink that stays inside the root`() {
        // Given
        val link = root.resolve("alias")
        Files.createSymbolicLink(link, root.resolve("readme.txt"))
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(link.toString()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "readme.txt", "../etc/passwd", "/etc/passwd", "/"])
    fun `rejects paths that are empty, relative or outside every root`(path: String) {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(path) }
    }

    @Test
    fun `rejects a path carrying a NUL character`() {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(root.toString() + "/read\u0000me.txt") }
    }

    @Test
    fun `rejects a file that does not exist`() {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(root.resolve("nothing.txt").toString()) }
    }

    @Test
    fun `resolves a destination that does not exist yet`() {
        // Given
        val sandbox = sandbox()
        val destination = root.resolve("media").resolve("new.txt")

        // When
        val resolved = sandbox.resolveForCreate(destination.toString())

        // Then
        resolved shouldBe root.toRealPath().resolve("media").resolve("new.txt")
    }

    @Test
    fun `rejects a destination whose parent does not exist`() {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveForCreate(root.resolve("nope/new.txt").toString()) }
    }

    @Test
    fun `rejects a destination outside of the roots`() {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.resolveForCreate(outside.resolve("new.txt").toString()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["..", ".", "a/b", "a\\b", "../x", "with\u0000nul", "line\nbreak", ""])
    fun `rejects a name that is not a single path element`(name: String) {
        // Given
        val sandbox = sandbox()

        // When / Then
        shouldThrow<FileBrowserException> { sandbox.validName(name) }
    }

    @Test
    fun `accepts an ordinary file name`() {
        // Given
        val sandbox = sandbox()

        // When
        val name = sandbox.validName("holiday photo (1).jpg")

        // Then
        name shouldBe "holiday photo (1).jpg"
    }

    @Test
    fun `refuses every path when the browser is disabled`() {
        // Given
        val sandbox = sandbox(enabled = false)

        // When / Then
        sandbox.enabled shouldBe false
        shouldThrow<FileBrowserException> { sandbox.resolveExisting(root.resolve("readme.txt").toString()) }
    }

    @Test
    fun `refuses writing in read mode`() {
        // Given
        val sandbox = sandbox(access = FileBrowserAccess.READ)

        // When / Then
        sandbox.writable shouldBe false
        shouldThrow<FileBrowserException> { sandbox.requireWritable() }
    }

    @Test
    fun `drops a configured root that does not exist and one nested in another`() {
        // Given
        val nested = root.resolve("media")

        // When
        val sandbox = sandbox(roots = listOf(root, nested, tempDir.resolve("gone")))

        // Then
        sandbox.roots shouldBe listOf(root.toRealPath())
    }
}
