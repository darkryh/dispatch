package com.ead.dispatch.sample.presentation.chat

import com.ead.dispatch.sample.domain.ChatMessage
import com.ead.dispatch.sample.domain.ChatRepository
import com.ead.dispatch.sample.domain.MessageAuthor
import com.ead.dispatch.sample.domain.ResponseSimulator
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatRepository,
    private val simulator: ResponseSimulator,
) : ViewModel() {
    val messages: StateFlow<List<ChatMessage>> = repository.messages

    private val mutableInput = MutableStateFlow("")
    val input: StateFlow<String> = mutableInput.asStateFlow()

    private val mutableStreaming = MutableStateFlow(false)
    val streamingState: StateFlow<Boolean> = mutableStreaming.asStateFlow()

    private var streamJob: Job? = null

    fun updateInput(value: String) {
        mutableInput.value = value
    }

    fun submit(value: String = input.value) {
        val prompt = value.trim()
        if (prompt.isEmpty() || mutableStreaming.value) return

        repository.add(MessageAuthor.USER, prompt)
        val responseId = repository.add(MessageAuthor.SAMPLE, "", isStreaming = true)
        mutableInput.value = ""
        mutableStreaming.value = true

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
                    mutableStreaming.value = false
                    streamJob = null
                }
            }
    }

    fun cancel() {
        streamJob?.cancel()
    }

    fun clearConversation() {
        cancel()
        repository.clear()
    }
}
