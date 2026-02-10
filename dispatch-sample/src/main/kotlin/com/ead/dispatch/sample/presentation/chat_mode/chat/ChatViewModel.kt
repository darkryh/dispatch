package com.ead.dispatch.sample.presentation.chat_mode.chat

import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.domain.util.extension.toCliMessage
import com.ead.dispatch.sample.navigation.*
import com.ead.dispatch.sample.presentation.chat_mode.chat.event.ChatEvent
import com.ead.dispatch.sample.presentation.commands.CommandAction
import com.ead.dispatch.viewmodel.ViewModel
import com.ead.dispatch.widget.DecisionSelection
import com.ead.koog.context.orchestrator.api.currentRemainingPercent
import com.ead.koog.context.orchestrator.api.remainingPercentFlow
import com.ead.koog.context.orchestrator.telemetry.ContextCheckpointProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

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
    private val _pendingDecision = MutableStateFlow<DecisionPromptPayload?>(null)
    internal val pendingDecision: StateFlow<DecisionPromptPayload?> = _pendingDecision.asStateFlow()
    private val _contextRemainingPercent = MutableStateFlow<Int?>(null)
    val contextRemainingPercent: StateFlow<Int?> = _contextRemainingPercent.asStateFlow()

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
        val latest = checkpoints.maxByOrNull { it.createdAt }

        val history = when {
            latest == null -> emptyList()
            latest.isTombstone() -> emptyList()
            latest.messageHistory.isNotEmpty() -> latest.messageHistory
            else -> checkpoints.flatMap { it.messageHistory }
        }

        val messages = history
            .filter { message -> message.role != Message.Role.System }
            .sortedBy { message -> message.metaInfo.timestamp }
            .distinctBy { message -> Triple(message.role, message.metaInfo.timestamp, message.content) }

        val restored = mutableListOf<CliMessage>()
        var pendingDecision: DecisionPromptPayload? = null
        messages.map { it.toCliMessage() }.forEach { cliMessage ->
            when (cliMessage.role) {
                CliMessageRole.TOOL -> {
                    val decision = parseDecisionPromptPayload(cliMessage.toolName, cliMessage.data)
                    if (decision != null) {
                        pendingDecision = decision
                    } else {
                        restored += cliMessage
                    }
                }
                CliMessageRole.USER -> {
                    if (pendingDecision != null) {
                        pendingDecision = null
                    }
                    restored += cliMessage
                }
                else -> restored += cliMessage
            }
        }

        _messages.value = restored
        _pendingDecision.value = pendingDecision
        _contextRemainingPercent.value = ContextCheckpointProperties.readRemainingPercent(latest?.properties)
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
                if (_pendingDecision.value != null) return
                val navigator = event.navigator
                val text = event.text

                val commandAction = commandManager.routing(text, writerMode.value)

                if (commandAction != null) {
                    onCommandAction(navigator, commandAction)
                    onEvent(ChatEvent.OnClearTextField)
                    return
                }


                onEvent(ChatEvent.OnClearTextField)
                submitMessage(text = text, fromDecisionPrompt = false)
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

    private fun onCommandAction(navigator: Navigator, commandAction: CommandAction) {
        when (commandAction) {
            CommandAction.ClearContext -> {
                _messages.value = emptyList()
                _pendingDecision.value = null
                _contextRemainingPercent.value = null
                val activeSessionId = _session.value?.id ?: route.conversationId
                if (!activeSessionId.isNullOrBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        clearPersistedContext(activeSessionId)
                    }
                }
            }
            is CommandAction.OpenEntityList -> {
                openEntityList(navigator, commandAction.type, _session.value?.id)
            }
            CommandAction.OpenStoryChat -> {
                navigator.navigate(StoryChatRoute(storyId = _session.value?.id))
            }
        }
    }

    /**
     * Process a submitted message based on the current mode.
     */
    private fun submitMessage(
        text: String,
        fromDecisionPrompt: Boolean,
    ) {
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

            val response = chatAgent.run(
                session = session,
                input = ChatRequest(
                    text = input,
                    storyId = session.id,
                    fromDecisionPrompt = fromDecisionPrompt,
                )
            )
            val assistantStreamingResponse = response.value

            val metadataJob = viewModelScope.launch(Dispatchers.IO) {
                response.metadata.remainingPercentFlow().collect { remainingPercent ->
                    _contextRemainingPercent.value = remainingPercent
                }
            }

            cancelRequested = false

            val job = viewModelScope.launch(Dispatchers.IO) {
                val currentJob = coroutineContext[Job]

                try {
                    assistantStreamingResponse.collect { frame ->
                        if (cancelRequested) {
                            return@collect
                        }
                        if (_pendingDecision.value != null && frame !is StreamFrame.End) {
                            // When a decision prompt is active, pause visible streaming until user responds.
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
                            val decision = parseDecisionPromptPayload(frame.name, frame.content)
                            if (decision != null) {
                                _pendingDecision.value = decision
                                _isProcessing.value = false
                            } else {
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

                    metadataJob.cancelAndJoin()
                    _contextRemainingPercent.value = response.metadata.currentRemainingPercent()

                    if (_contextRemainingPercent.value == null) {
                        refreshContextStatus(session.id)
                    }

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

    fun onDecisionSelected(selection: DecisionSelection) {
        _pendingDecision.value ?: return
        val selectedText = when (selection) {
            is DecisionSelection.Option -> selection.option.label
            is DecisionSelection.Custom -> selection.text.trim()
        }.trim()
        if (selectedText.isBlank()) return

        _pendingDecision.value = null
        submitMessage(selectedText, fromDecisionPrompt = true)
    }

    private fun openEntityList(
        navigator: Navigator,
        type: EntityOptionType,
        storyId: String?,
    ) {
        when (type) {
            EntityOptionType.CHARACTERS -> navigator.navigate(CharacterListRoute(storyId = storyId))
            EntityOptionType.LOCATIONS -> navigator.navigate(LocationListRoute(storyId = storyId))
            EntityOptionType.ARCS -> navigator.navigate(ArcListRoute(storyId = storyId))
            EntityOptionType.WORLD_RULES -> navigator.navigate(WorldRuleListRoute(storyId = storyId))
            EntityOptionType.CULTURES -> navigator.navigate(CultureListRoute(storyId = storyId))
            EntityOptionType.EVENTS -> navigator.navigate(EventListRoute(storyId = storyId))
            EntityOptionType.ORGANIZATIONS -> navigator.navigate(OrganizationListRoute(storyId = storyId))
            EntityOptionType.RELATIONSHIPS -> navigator.navigate(RelationshipListRoute(storyId = storyId))
            EntityOptionType.LOCATION_FEATURES -> navigator.navigate(LocationFeatureListRoute(storyId = storyId))
            EntityOptionType.ARTIFACTS -> navigator.navigate(ArtifactListRoute(storyId = storyId))
            EntityOptionType.TIMELINE -> navigator.navigate(TimelineListRoute(storyId = storyId))
            EntityOptionType.VOLUMES,
            EntityOptionType.CHAPTERS,
            EntityOptionType.SCENES -> {
                // Story mode lists not implemented yet.
            }
        }
    }

    private suspend fun activeSession(firstMessagePreview: String): Session {
        val existing = _session.value
        if (existing != null) {
            return existing
        }

        val current = sessionManager.getCurrentSession()
        if (current != null) {
            _session.value = current
            return current
        }

        val session = sessionManager.createSession(title = firstMessagePreview)
        _session.value = session
        return session
    }

    private suspend fun refreshContextStatus(sessionId: String) {
        val latestCheckpoint = storageProvider.getLatestCheckpoint(AIProvider.getChatAgentId(sessionId))
        _contextRemainingPercent.value = ContextCheckpointProperties.readRemainingPercent(latestCheckpoint?.properties)
    }

    private suspend fun clearPersistedContext(sessionId: String) {
        val agentId = AIProvider.getChatAgentId(sessionId)
        val latest = storageProvider.getLatestCheckpoint(agentId)
        val version = (latest?.version?.plus(1) ?: 0L).coerceAtLeast(0L)
        val tombstone = tombstoneCheckpoint(Clock.System.now(), version)
        storageProvider.saveCheckpoint(agentId, tombstone)
    }
}
