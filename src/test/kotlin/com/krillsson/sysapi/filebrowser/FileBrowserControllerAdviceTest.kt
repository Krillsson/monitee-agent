package com.krillsson.sysapi.filebrowser

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException

class FileBrowserControllerAdviceTest {

    private val advice = FileBrowserControllerAdvice()

    @ParameterizedTest
    @CsvSource(
        "REFUSED, 400",
        "NOT_FOUND, 404",
        "ALREADY_EXISTS, 409",
        "NOT_EMPTY, 409",
        "CANCELLED, 409",
        "PERMISSION_DENIED, 403",
        "READ_ONLY_FILE_SYSTEM, 403",
        "NOT_SUPPORTED, 415",
        "OUT_OF_SPACE, 507",
        "IO_ERROR, 500"
    )
    fun `answers a download or an upload with the status that matches what went wrong`(
        type: FileBrowserErrorType,
        status: Int
    ) {
        // Given
        val failure = FileBrowserException("something happened", type)

        // When
        val response = advice.handleRefusal(failure)

        // Then
        response.statusCode.value() shouldBe status
        response.body.shouldNotBeNull().type shouldBe type
        response.body.shouldNotBeNull().reason shouldBe "something happened"
    }

    @Test
    fun `classifies a full disk reported straight out of a stream as insufficient storage`() {
        // Given
        val thrown = IOException("No space left on device")

        // When
        val response = advice.handleFailure(thrown)

        // Then
        response.statusCode.value() shouldBe 507
        response.body.shouldNotBeNull().type shouldBe FileBrowserErrorType.OUT_OF_SPACE
    }
}
