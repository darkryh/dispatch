package com.ead.dispatch.sample.presentation.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.navigate
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.domain.util.extension.toCliMessage
import com.ead.dispatch.sample.navigation.ChatRoute
import com.ead.dispatch.sample.navigation.EntityListRoute
import com.ead.dispatch.sample.navigation.StoryChatRoute
import com.ead.dispatch.sample.presentation.chat.event.ChatEvent
import com.ead.dispatch.sample.presentation.commands.CommandAction
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * ViewModel for the chat screen with stub responses.
 */
class ChatViewModel(
    private val commandManager: CommandManager,
    private val sessionManager: SessionManager,
    private val chatAgent: ChatAgent,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ChatRoute>()
    private val storageProvider = Storage.provider

    // Current session state
    private val _session = MutableStateFlow<Session?>(null)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _messages = MutableStateFlow(emptyList<CliMessage>())
    val messages: StateFlow<List<CliMessage>> = _messages.asStateFlow()

    // Writer Assistant mode state
    private val _writerMode = MutableStateFlow(WriterMode.CHAT)
    val writerMode: StateFlow<WriterMode> = _writerMode.asStateFlow()

    private var countWriterMode = 0
    private var activeStreamJob: Job? = null
    private var cancelRequested = false

    init {
        val sessionId = route.conversationId?.trim().takeIf { !it.isNullOrEmpty() }

        if (sessionId != null) {
            viewModelScope.launch(Dispatchers.IO) {
                _session.value = sessionManager.ensureSession(sessionId)
                loadMessagesForSession(sessionId)
            }
        }
    }

    private suspend fun loadMessagesForSession(sessionId: String) {
        val checkpoints = storageProvider.getCheckpoints(AIProvider.getChatAgentId(sessionId))

        val history = checkpoints
            .maxByOrNull { it.createdAt }
            ?.messageHistory
            ?.takeIf { it.isNotEmpty() }
            ?: checkpoints.flatMap { it.messageHistory }

        val messages = history
            .filter { message -> message.role != Message.Role.System }
            .sortedBy { message -> message.metaInfo.timestamp }
            .distinctBy { message -> Triple(message.role, message.metaInfo.timestamp, message.content) }

        _messages.value = messages.map { it.toCliMessage() }
    }

    fun onEvent(event: ChatEvent) {
        when (event) {
            is ChatEvent.OnClearTextField -> {
                _inputText.value = ""
            }
            is ChatEvent.OnTextChanged -> {
                _inputText.value = event.text
            }
            is ChatEvent.OnSubmitMessage -> {
                val backStack = event.backStack
                val text = event.text

                val commandAction = commandManager.routing(text, writerMode.value)

                if (commandAction != null) {
                    onCommandAction(backStack, commandAction)
                    onEvent(ChatEvent.OnClearTextField)
                    return
                }


                onEvent(ChatEvent.OnClearTextField)
                submitMessage(text = text)
            }
            is ChatEvent.OnChatModeChanged -> {
                countWriterMode++

                if (countWriterMode > WriterMode.entries.size - 1) {
                    countWriterMode = 0
                }

                _writerMode.value =  when (countWriterMode) {
                    0 -> WriterMode.CHAT
                    1 -> WriterMode.CHAT_STORY
                    else -> WriterMode.CHAT
                }
            }
            is ChatEvent.OnCancelProcessing -> {
                cancelRequested = true
                activeStreamJob?.cancel()
                activeStreamJob = null
                _isProcessing.value = false
            }
        }
    }

    private fun onCommandAction(backStack: NavBackStack<NavKey>, commandAction: CommandAction) {
        when (commandAction) {
            CommandAction.ClearContext -> {
                _messages.value = emptyList()
            }
            is CommandAction.OpenEntityList -> {
                backStack.navigate(EntityListRoute(type = commandAction.type, storyId = _session.value?.id))
            }
            CommandAction.OpenStoryChat -> {
                backStack.navigate(StoryChatRoute(storyId = _session.value?.id))
            }
        }
    }

    /**
     * Process a submitted message based on the current mode.
     */
    private fun submitMessage(text: String) {
        val input = text.trim()
        if (input.isBlank()) {
            return
        }

        viewModelScope.launch {
            val session = activeSession(input)

            _messages.update { messages ->
                messages + CliMessage(
                    data = input,
                    role = CliMessageRole.USER
                )
            }

            _isProcessing.value = true

            val assistantStreamingResponse = chatAgent.run(
                session = session,
                input = ChatRequest(
                    text = input,
                    storyId = session.id,
                )
            )

            cancelRequested = false
            val job = viewModelScope.launch(Dispatchers.IO) {
                val currentJob = coroutineContext[Job]
                try {
                    assistantStreamingResponse.collect { frame ->
                        if (cancelRequested) {
                            return@collect
                        }
                    when (frame) {
                        is StreamFrame.Append -> {
                            _isProcessing.value = true
                            if (frame.text.isEmpty()) {
                                return@collect
                            }
                            _messages.update { messages ->
                                if (messages.isEmpty()) {
                                    return@update messages + CliMessage(
                                        data = frame.text,
                                        role = CliMessageRole.ASSISTANT
                                    )
                                }

                                val updated = messages.toMutableList()
                                val lastIndex = updated.lastIndex
                                val existing = updated[lastIndex]

                                if (existing.role != CliMessageRole.ASSISTANT) {
                                    updated.add(
                                        CliMessage(
                                            data = frame.text,
                                            role = CliMessageRole.ASSISTANT
                                        )
                                    )
                                } else {
                                    updated[lastIndex] = existing.copy(data = existing.data + frame.text)
                                }

                                updated
                            }
                        }
                        is StreamFrame.ToolCall -> {
                            _isProcessing.value = true

                            _messages.update { messages ->
                                messages + CliMessage(
                                    toolId = frame.id,
                                    toolName = frame.name,
                                    data = frame.content,
                                    role = CliMessageRole.TOOL
                                )
                            }
                        }
                        is StreamFrame.End -> {
                            // Update session after receiving response (increment count again)
                            sessionManager.updateSession(
                                sessionId = session.id,
                                title = _messages.value.lastOrNull { it.role == CliMessageRole.USER }?.data,
                                incrementMessageCount = true
                            )
                            _isProcessing.value = false
                            if (activeStreamJob === currentJob) {
                                activeStreamJob = null
                            }
                        }
                    }
                    }
                } finally {
                    if (activeStreamJob === currentJob) {
                        activeStreamJob = null
                    }
                    _isProcessing.value = false
                }
            }
            activeStreamJob = job

            onEvent(ChatEvent.OnClearTextField)
        }
    }

    private suspend fun activeSession(firstMessagePreview: String): Session {
        val existing = _session.value
        if (existing != null) {
            return existing
        }

        val session = sessionManager.createSession(title = firstMessagePreview)
        _session.value = session
        return session
    }
}
