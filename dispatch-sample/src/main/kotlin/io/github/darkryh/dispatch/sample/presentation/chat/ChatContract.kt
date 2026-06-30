package io.github.darkryh.dispatch.sample.presentation.chat

import io.github.darkryh.dispatch.sample.domain.ChatMessage

/**
 * MVI contract for the chat screen.
 *
 * [ChatState] is the single immutable snapshot the screen renders; [ChatIntent]s are the only way
 * the screen mutates it; [ChatEffect]s are one-shot signals (consumed via `collectSideEffect`) that
 * must not be replayed on recomposition.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isStreaming: Boolean = false,
)

sealed interface ChatIntent {
    /** The composer text changed. */
    data class UpdateInput(
        val text: String,
    ) : ChatIntent

    /** Send [text] as a user message and start streaming a simulated reply. */
    data class Submit(
        val text: String,
    ) : ChatIntent

    /** Stop the in-flight streaming reply (Esc while streaming). */
    data object Cancel : ChatIntent

    /** Clear the conversation back to its welcome state. */
    data object Clear : ChatIntent
}

sealed interface ChatEffect {
    /** Ask the view to reveal the newest message. */
    data object ScrollToBottom : ChatEffect
}
