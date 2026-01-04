package com.ead.dispatch.sample.domain.model.session

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a resumable session/conversation.
 *
 * @param id Unique session identifier (conversation ID)
 * @param title Session title or conversation preview text
 * @param updatedAt Last update timestamp
 * @param messageCount Number of messages in the session (optional metadata)
 */
@Serializable
data class Session(
    val id: String,
    val title: String,
    val updatedAt: Instant = Clock.System.now(),
    val messageCount: Int = 0,
)
