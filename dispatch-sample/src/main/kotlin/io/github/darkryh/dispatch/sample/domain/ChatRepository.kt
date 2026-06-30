package io.github.darkryh.dispatch.sample.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

class ChatRepository {
    private val ids = AtomicLong(0)
    private val mutableMessages = MutableStateFlow(welcomeMessages())

    val messages: StateFlow<List<ChatMessage>> = mutableMessages.asStateFlow()

    fun add(
        author: MessageAuthor,
        text: String,
        isStreaming: Boolean = false,
    ): Long {
        val id = ids.incrementAndGet()
        mutableMessages.update { it + ChatMessage(id, author, text, isStreaming) }
        return id
    }

    fun update(
        id: Long,
        text: String,
        isStreaming: Boolean,
    ) {
        mutableMessages.update { messages ->
            messages.map { message ->
                if (message.id == id) message.copy(text = text, isStreaming = isStreaming) else message
            }
        }
    }

    fun clear() {
        mutableMessages.value = welcomeMessages()
    }

    private fun welcomeMessages(): List<ChatMessage> =
        listOf(
            ChatMessage(
                id = ids.incrementAndGet(),
                author = MessageAuthor.SAMPLE,
                text =
                    "This conversation is fully local. Send a message to exercise streaming, " +
                        "cancellation, scrolling, and input history.",
            ),
        )
}
