package com.ead.dispatch.sample.data.db.entities

import com.ead.dispatch.sample.data.db.type.SessionMode

data class SessionRecord(
    /** Stable identifier for the session. */
    val id: String,
    /** Profile data grouped for readability. */
    val profile: SessionProfile,
    /** Creation time for ordering and audits. */
    val createdAt: Long,
    /** Last update time for UI refresh. */
    val updatedAt: Long,
    /** Usage stats grouped for readability. */
    val stats: SessionStats,
    /** Session-level metadata used for tool routing or settings. */
    val metadata: Map<String, String> = emptyMap(),
) {
    data class SessionProfile(
        /** Display title shown in session lists. */
        val title: String,
        /** Mode label (chat or story) for UI flow. */
        val mode: SessionMode,
    )

    data class SessionStats(
        /** Message count for chat summaries. */
        val messageCount: Long,
    )
}
