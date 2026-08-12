package com.krillsson.sysapi.filebrowser

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import java.time.Instant

class FileUploadControllerTest {

    private val manager = mockk<FileBrowserManager>()
    private val controller = FileUploadController(manager)

    private fun request(contentType: String?): HttpServletRequest {
        val request = mockk<HttpServletRequest>()
        every { request.contentType } returns contentType
        every { request.inputStream } returns mockk<ServletInputStream>()
        return request
    }

    private fun entry(name: String) = FileEntry(
        name = name,
        path = "/storage/$name",
        type = FileEntryType.FILE,
        sizeBytes = 3,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        mimeType = "text/plain",
        iconId = "txt",
        editable = true,
        openableAsLog = true
    )

    @ParameterizedTest
    @ValueSource(
        strings = [
            "text/plain",
            "text/plain;charset=UTF-8",
            "TEXT/PLAIN",
            "application/x-www-form-urlencoded",
            "multipart/form-data; boundary=----WebKitFormBoundary"
        ]
    )
    fun `refuses the content types a page can post from another origin`(contentType: String) {
        // Given
        val request = request(contentType)

        // When / Then
        shouldThrow<UnsupportedUploadContentTypeException> {
            controller.upload("/storage", "notes.txt", false, request)
        }
        verify(exactly = 0) { manager.upload(any(), any(), any(), any()) }
    }

    @Test
    fun `accepts a body sent as its own content type`() {
        // Given
        every { manager.upload(any(), any(), any(), any()) } returns entry("notes.txt")

        // When
        val response = controller.upload("/storage", "notes.txt", false, request("application/octet-stream"))

        // Then
        response.statusCode shouldBe HttpStatus.CREATED
        response.body shouldBe entry("notes.txt")
    }

    @Test
    fun `accepts a body that came without a content type`() {
        // Given
        every { manager.upload(any(), any(), any(), any()) } returns entry("notes.txt")

        // When
        val response = controller.upload("/storage", "notes.txt", false, request(null))

        // Then
        response.statusCode shouldBe HttpStatus.CREATED
    }
}
