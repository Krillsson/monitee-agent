package com.krillsson.sysapi.graphql.mutations

import com.krillsson.sysapi.core.domain.event.OngoingEvent
import java.util.UUID

data class AcknowledgeEventInput(
    val eventId: UUID,
    val note: String?
)

data class UnacknowledgeEventInput(
    val eventId: UUID
)

interface AcknowledgeEventOutput

data class AcknowledgeEventOutputSucceeded(
    val event: OngoingEvent
) : AcknowledgeEventOutput

data class AcknowledgeEventOutputFailed(
    val reason: String
) : AcknowledgeEventOutput
