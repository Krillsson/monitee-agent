package com.krillsson.sysapi.core.check

import com.krillsson.sysapi.bash.CommandArgument

object CheckHost {

    fun isValid(host: String) = CommandArgument.isHost(host)
}
