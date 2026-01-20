package com.ead.dispatch.sample.domain.model.message

/**
 * Represents the role of a message in the chat.
 */
enum class CliMessageRole {
    /** Message from the user */
    USER,

    /** Response from the assistant */
    ASSISTANT,
    TOOL,

    /** System message (commands, notifications) */
    SYSTEM
}
