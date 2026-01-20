package com.ead.dispatch.sample.domain.model.message

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.time.ExperimentalTime

data class CliMessage @OptIn(ExperimentalTime::class) constructor(
    val data: String,
    val role: CliMessageRole,
    val timestamp : Instant = Clock.System.now(),
    val toolId : String? = null,
    val toolName : String? = null,
)
