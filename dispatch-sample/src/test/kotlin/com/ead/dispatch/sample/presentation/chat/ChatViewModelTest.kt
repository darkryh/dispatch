package com.ead.dispatch.sample.presentation.chat

import com.ead.dispatch.sample.domain.ChatRepository
import com.ead.dispatch.sample.domain.MessageAuthor
import com.ead.dispatch.sample.domain.ResponseSimulator
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatViewModelTest {
    @Test
    fun `submit streams chunks into one completed response`(): Unit =
        runBlocking {
            val repository = ChatRepository()
            val viewModel = ChatViewModel(repository, ResponseSimulator { flowOf("one ", "two", " three") })

            viewModel.updateInput("hello")
            viewModel.submit()
            withTimeout(2_000) { viewModel.streamingState.first { !it } }

            val messages = repository.messages.value
            assertEquals("hello", messages[messages.lastIndex - 1].text)
            assertEquals(MessageAuthor.USER, messages[messages.lastIndex - 1].author)
            assertEquals("one two three", messages.last().text)
            assertFalse(messages.last().isStreaming)
            assertEquals("", viewModel.input.value)
            viewModel.clear()
        }

    @Test
    fun `cancel keeps partial response and ends streaming state`(): Unit =
        runBlocking {
            val repository = ChatRepository()
            val simulator =
                ResponseSimulator {
                    flow {
                        emit("partial")
                        awaitCancellation()
                    }
                }
            val viewModel = ChatViewModel(repository, simulator)

            viewModel.submit("cancel this")
            withTimeout(2_000) { repository.messages.first { it.last().text == "partial" } }
            viewModel.cancel()
            withTimeout(2_000) { viewModel.streamingState.first { !it } }

            assertEquals(
                "partial\n[cancelled]",
                repository.messages.value
                    .last()
                    .text,
            )
            assertFalse(
                repository.messages.value
                    .last()
                    .isStreaming,
            )
            viewModel.clear()
        }

    @Test
    fun `repository preserves conversation across view models`(): Unit =
        runBlocking {
            val repository = ChatRepository()
            val simulator = ResponseSimulator { flowOf("reply") }
            val first = ChatViewModel(repository, simulator)

            first.submit("persist me")
            withTimeout(2_000) { first.streamingState.first { !it } }
            first.clear()

            val second = ChatViewModel(repository, simulator)
            assertTrue(second.messages.value.any { it.text == "persist me" })
            assertEquals(
                "reply",
                second.messages.value
                    .last()
                    .text,
            )
            second.clear()
        }
}
