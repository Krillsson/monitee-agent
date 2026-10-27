package com.krillsson.sysapi.core.check

import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import javax.naming.ldap.LdapName
import javax.security.auth.x500.X500Principal

data class CheckCertificate(
    val expiresAt: Instant,
    val issuedAt: Instant?,
    val issuer: String?,
    val subject: String?
) {
    companion object {
        private const val SECONDS_PER_DAY = 86_400L
    }

    fun daysUntilExpiry(now: Instant): Int =
        Math.floorDiv(Duration.between(now, expiresAt).seconds, SECONDS_PER_DAY).toInt()
}

fun X509Certificate.asCheckCertificate() = CheckCertificate(
    expiresAt = notAfter.toInstant(),
    issuedAt = notBefore.toInstant(),
    issuer = issuerX500Principal.commonNameOrDistinguishedName(),
    subject = subjectX500Principal.commonNameOrDistinguishedName()
)

fun X500Principal.commonNameOrDistinguishedName(): String {
    val distinguishedName = getName(X500Principal.RFC2253)
    return LdapName(distinguishedName).rdns
        .lastOrNull { it.type.equals("CN", ignoreCase = true) }
        ?.value
        ?.toString()
        ?: distinguishedName
}
