package com.krillsson.sysapi.core.check

object CheckHost {

    private val PATTERN = Regex("""[A-Za-z0-9._:%\[\]-]+""")

    fun isValid(host: String) = PATTERN.matches(host)
}
