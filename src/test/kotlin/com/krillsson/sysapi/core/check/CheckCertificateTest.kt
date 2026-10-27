package com.krillsson.sysapi.core.check

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import javax.security.auth.x500.X500Principal

class CheckCertificateTest {

    private fun certificateExpiringAt(expiresAt: String) = CheckCertificate(
        expiresAt = Instant.parse(expiresAt),
        issuedAt = null,
        issuer = null,
        subject = null
    )

    @ParameterizedTest
    @CsvSource(
        "2026-08-24T10:00:00Z, 23",
        "2026-08-24T09:59:59Z, 22",
        "2026-08-01T10:00:00Z, 0",
        "2026-08-01T09:59:59Z, -1",
        "2026-07-29T10:00:00Z, -3"
    )
    fun `counts whole days until expiry and goes negative once expired`(expiresAt: String, days: Int) {
        // Given
        val certificate = certificateExpiringAt(expiresAt)
        val now = Instant.parse("2026-08-01T10:00:00Z")

        // When
        val daysUntilExpiry = certificate.daysUntilExpiry(now)

        // Then
        daysUntilExpiry shouldBe days
    }

    @Test
    fun `names a principal by its common name`() {
        // Given
        val principal = X500Principal("CN=R11, O=Let's Encrypt, C=US")

        // When
        val name = principal.commonNameOrDistinguishedName()

        // Then
        name shouldBe "R11"
    }

    @Test
    fun `names a principal by its distinguished name when it has no common name`() {
        // Given
        val principal = X500Principal("OU=Home Lab, O=Example, C=SE")

        // When
        val name = principal.commonNameOrDistinguishedName()

        // Then
        name shouldBe "OU=Home Lab,O=Example,C=SE"
    }

    @Test
    fun `has no certificate on a result that carries no expiry`() {
        // Given
        val result = checkResult(Instant.parse("2026-08-01T10:00:00Z")).asDomain()

        // When
        val certificate = result.certificate()

        // Then
        certificate shouldBe null
    }
}
