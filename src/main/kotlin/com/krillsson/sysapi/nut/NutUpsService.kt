package com.krillsson.sysapi.nut

import org.springframework.stereotype.Service

@Service
class NutUpsService {

    sealed class Status {
        object Available : Status()
        object Disabled : Status()
        data class Unavailable(val error: RuntimeException) : Status()
    }
}