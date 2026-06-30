package io.github.darkryh.dispatch.sample.domain

enum class MessageAuthor {
    USER,
    SAMPLE,
}

data class ChatMessage(
    val id: Long,
    val author: MessageAuthor,
    val text: String,
    val isStreaming: Boolean = false,
)
