package com.krillsson.sysapi.graphql.mutations

import java.util.*

data class DeleteEventsForMonitorInput(
        val monitorId: UUID
)