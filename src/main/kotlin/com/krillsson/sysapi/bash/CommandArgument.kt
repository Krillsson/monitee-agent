package com.krillsson.sysapi.bash

object CommandArgument {

    private const val MAX_LENGTH = 255

    private val COMMAND_NAME = Regex("""[A-Za-z0-9][A-Za-z0-9._-]*""")
    private val UNIT_NAME = Regex("""[A-Za-z0-9:_.\\@][A-Za-z0-9:_.\\@-]*""")
    private val UPS_NAME = Regex("""[A-Za-z0-9_.][A-Za-z0-9_.-]*""")
    private val DEVICE_PATH = Regex("""[A-Za-z0-9/_.:][A-Za-z0-9/_.:-]*""")
    private val HOST = Regex("""[A-Za-z0-9._:%\[\]][A-Za-z0-9._:%\[\]-]*""")

    fun isCommandName(value: String) = matches(COMMAND_NAME, value)

    fun isUnitName(value: String) = matches(UNIT_NAME, value)

    fun isUpsName(value: String) = matches(UPS_NAME, value)

    fun isDevicePath(value: String) = matches(DEVICE_PATH, value)

    fun isHost(value: String) = matches(HOST, value)

    private fun matches(pattern: Regex, value: String) = value.length <= MAX_LENGTH && pattern.matches(value)
}
