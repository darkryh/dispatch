package com.ead.dispatch.sample.domain.model.session

/**
 * Enum defining which columns can be displayed in the SessionSelector.
 * Allows configurable column visibility via parameters.
 */
enum class SessionColumn {
    /** Relative time since last update (e.g., "15 hours ago") */
    UPDATED_TIME,

    /** Unique conversation/session ID */
    CONVERSATION_ID,

    /** Session title or conversation preview */
    TITLE,

    /** Number of messages in the session */
    MESSAGE_COUNT,
}

/**
 * Default columns to display in the SessionSelector.
 */
val DEFAULT_SESSION_COLUMNS = listOf(
    SessionColumn.UPDATED_TIME,
    SessionColumn.CONVERSATION_ID,
    SessionColumn.TITLE,
)
