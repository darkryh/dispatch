package io.github.darkryh.dispatch.sample.presentation.chat

import io.github.darkryh.dispatch.sample.domain.ChatRepository
import io.github.darkryh.dispatch.sample.domain.MessageAuthor
import io.github.darkryh.dispatch.sample.domain.ResponseSimulator
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

/**
 * Unit tests for the MVI [ChatViewModel]. The view is never involved: tests drive the model purely
 * through [ChatViewModel.sendIntent] and assert on the published [ChatViewModel.state]. The
 * streaming/cancel mechanism is exercised against a deterministic in-memory [ResponseSimulator].
 */
class ChatViewModelTest {
    @Test
    fun `submit streams chunks into one completed response`(): Unit =
        runBlocking {
            val repository = ChatRepository()
            val viewModel = ChatViewModel(repository, ResponseSimulator { flowOf("one ", "two", " three") })

            viewModel.sendIntent(ChatIntent.UpdateInput("hello"))
            viewModel.sendIntent(ChatIntent.Submit("hello"))

            val finished =
                withTimeout(2_000) {
                    viewModel.state.first { !it.isStreaming && it.messages.last().text == "one two three" }
                }

            val messages = finished.messages
            assertEquals("hello", messages[messages.lastIndex - 1].text)
            assertEquals(MessageAuthor.USER, messages[messages.lastIndex - 1].author)
            assertEquals("one two three", messages.last().text)
            assertFalse(messages.last().isStreaming)
            assertEquals("", finished.input)
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

            viewModel.sendIntent(ChatIntent.Submit("cancel this"))
            withTimeout(2_000) { viewModel.state.first { it.messages.last().text == "partial" } }
            viewModel.sendIntent(ChatIntent.Cancel)

            // Wait for the cancellation to be fully reflected (partial text marked + streaming ended).
            val finished =
                withTimeout(2_000) {
                    viewModel.state.first {
                        !it.isStreaming &&
                            it.messages
                                .last()
                                .text
                                .contains("[cancelled]")
                    }
                }
            assertEquals("partial\n[cancelled]", finished.messages.last().text)
            assertFalse(finished.messages.last().isStreaming)
            viewModel.clear()
        }

    @Test
    fun `repository preserves conversation across view models`(): Unit =
        runBlocking {
            val repository = ChatRepository()
            val simulator = ResponseSimulator { flowOf("reply") }
            val first = ChatViewModel(repository, simulator)

            first.sendIntent(ChatIntent.Submit("persist me"))
            withTimeout(2_000) { first.state.first { !it.isStreaming && it.messages.last().text == "reply" } }
            first.clear()

            val second = ChatViewModel(repository, simulator)
            assertTrue(
                second.state.value.messages
                    .any { it.text == "persist me" },
            )
            assertEquals(
                "reply",
                second.state.value.messages
                    .last()
                    .text,
            )
            second.clear()
        }
}
