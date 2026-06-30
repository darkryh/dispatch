package io.github.darkryh.dispatch.sample.presentation.chat

import io.github.darkryh.dispatch.sample.domain.ChatRepository
import io.github.darkryh.dispatch.sample.domain.MessageAuthor
import io.github.darkryh.dispatch.sample.domain.ResponseSimulator
import io.github.darkryh.dispatch.viewmodel.FullMviViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Drives the streaming-chat screen, modelled on [FullMviViewModel] so the screen is a pure function
 * of [ChatState] plus one-shot [ChatEffect]s.
 *
 * The streaming *mechanism* is deliberately unchanged from earlier versions of the sample — a single
 * [streamJob] collecting [ResponseSimulator.stream], cancellable mid-flight, marking partial output
 * as `[cancelled]`. Only the architecture (MVI) and the UI around it were rebuilt.
 *
 * [streamJob] is the one piece of non-MVI mutable state, justified because a coroutine handle is not
 * part of the rendered state; everything the view sees still flows through [ChatState].
 */
class ChatViewModel(
    private val repository: ChatRepository,
    private val simulator: ResponseSimulator,
) : FullMviViewModel<ChatState, ChatIntent, ChatEffect>(
        ChatState(messages = repository.messages.value),
    ) {
    private var streamJob: Job? = null

    init {
        // Mirror the repository (the source of truth for messages) into the rendered state.
        viewModelScope.launch {
            repository.messages.collect { messages ->
                updateState { it.copy(messages = messages) }
            }
        }
    }

    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.UpdateInput -> updateState { it.copy(input = intent.text) }
            is ChatIntent.Submit -> submit(intent.text)
            ChatIntent.Cancel -> streamJob?.cancel()
            ChatIntent.Clear -> {
                streamJob?.cancel()
                repository.clear()
            }
        }
    }

    private fun submit(value: String) {
        val prompt = value.trim()
        if (prompt.isEmpty() || currentState.isStreaming) return

        repository.add(MessageAuthor.USER, prompt)
        val responseId = repository.add(MessageAuthor.SAMPLE, "", isStreaming = true)
        updateState { it.copy(input = "", isStreaming = true) }
        emitSideEffect(ChatEffect.ScrollToBottom)

        streamJob =
            viewModelScope.launch {
                val response = StringBuilder()
                try {
                    simulator.stream(prompt).collect { chunk ->
                        response.append(chunk)
                        repository.update(responseId, response.toString(), isStreaming = true)
                    }
                    repository.update(responseId, response.toString(), isStreaming = false)
                } catch (cancelled: CancellationException) {
                    val partial = response.toString().ifEmpty { "Response cancelled before the first chunk." }
                    repository.update(responseId, "$partial\n[cancelled]", isStreaming = false)
                    throw cancelled
                } finally {
                    updateState { it.copy(isStreaming = false) }
                    streamJob = null
                }
            }
    }
}
