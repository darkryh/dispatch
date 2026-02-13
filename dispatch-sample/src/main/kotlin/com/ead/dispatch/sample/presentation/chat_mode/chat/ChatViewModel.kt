package com.ead.dispatch.sample.presentation.chat_mode.chat

import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.feature.AgentCheckpointData
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
import com.ead.dispatch.sample.domain.agents.StoryAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
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

private data class ModeHistoryState(
    val messages: List<CliMessage> = emptyList(),
    val pendingDecision: DecisionPromptPayload? = null,
    val contextRemainingPercent: Int? = null,
)

/**
 * ViewModel for the chat screen with stub responses.
 */
class ChatViewModel(
    private val commandManager: CommandManager,
    private val sessionManager: SessionManager,
    private val chatAgent: ChatAgent,
    private val storyAgent: StoryAgent,
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

    private val modeStateLock = Any()
    private val modeHistories = WriterMode.entries
        .associateWith { ModeHistoryState() }
        .toMutableMap()

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
        val chatState = restoreModeState(
            checkpoints = storageProvider.getCheckpoints(AIProvider.getChatAgentId(sessionId))
        )
        val storyState = restoreModeState(
            checkpoints = storageProvider.getCheckpoints(AIProvider.getStoryAgentId(sessionId))
        )
        setModeState(WriterMode.CHAT, chatState)
        setModeState(WriterMode.CHAT_STORY, storyState)
        applyModeState(_writerMode.value)
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

                submitMessage(text = text, fromDecisionPrompt = false)
            }
            is ChatEvent.OnChatModeChanged -> {
                if (_isProcessing.value || _pendingDecision.value != null) return
                val nextMode = when (_writerMode.value) {
                    WriterMode.CHAT -> WriterMode.CHAT_STORY
                    WriterMode.CHAT_STORY -> WriterMode.CHAT
                }
                _writerMode.value = nextMode
                applyModeState(nextMode)
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
                val activeMode = _writerMode.value
                setModeState(activeMode, ModeHistoryState())
                val activeSessionId = _session.value?.id ?: route.conversationId
                if (!activeSessionId.isNullOrBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        clearPersistedContext(activeSessionId, activeMode)
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

        val mode = writerMode.value
        appendMessage(
            mode = mode,
            message = CliMessage(
                data = input,
                role = CliMessageRole.USER
            )
        )
        _inputText.value = ""
        _isProcessing.value = true

        viewModelScope.launch {
            val session = activeSession(input)

            val response = when (mode) {
                WriterMode.CHAT -> chatAgent.run(
                    session = session,
                    input = ChatRequest(
                        text = input,
                        storyId = session.id,
                        fromDecisionPrompt = fromDecisionPrompt,
                    )
                )
                WriterMode.CHAT_STORY -> storyAgent.run(
                    session = session,
                    input = StoryRequest(
                        text = input,
                        storyId = session.id,
                        fromDecisionPrompt = fromDecisionPrompt,
                    )
                )
            }
            val assistantStreamingResponse = response.value

            val metadataJob = viewModelScope.launch(Dispatchers.IO) {
                response.metadata.remainingPercentFlow().collect { remainingPercent ->
                    setContextRemainingPercent(mode, remainingPercent)
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
                        if (modeState(mode).pendingDecision != null && frame !is StreamFrame.End) {
                            // When a decision prompt is active, pause visible streaming until user responds.
                            return@collect
                        }

                    when (frame) {
                        is StreamFrame.Append -> {
                            _isProcessing.value = true
                            if (frame.text.isEmpty()) {
                                return@collect
                            }
                            appendAssistantChunk(mode, frame.text)
                        }
                        is StreamFrame.ToolCall -> {
                            val decision = parseDecisionPromptPayload(frame.name, frame.content)
                            if (decision != null) {
                                setPendingDecision(mode, decision)
                                _isProcessing.value = false
                            } else {
                                _isProcessing.value = true
                                appendMessage(
                                    mode = mode,
                                    message = CliMessage(
                                        toolId = frame.id,
                                        toolName = frame.name,
                                        data = frame.content,
                                        role = CliMessageRole.TOOL
                                    )
                                )
                            }
                        }
                        is StreamFrame.End -> {
                            // Update session after receiving response (increment count again)
                            sessionManager.updateSession(
                                sessionId = session.id,
                                title = modeState(mode).messages.lastOrNull { it.role == CliMessageRole.USER }?.data,
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
                    setContextRemainingPercent(mode, response.metadata.currentRemainingPercent())

                    if (modeState(mode).contextRemainingPercent == null) {
                        refreshContextStatus(session.id, mode)
                    }

                    if (activeStreamJob === currentJob) {
                        activeStreamJob = null
                    }
                    _isProcessing.value = false
                }
            }
            activeStreamJob = job
        }
    }

    fun onDecisionSelected(selection: DecisionSelection) {
        _pendingDecision.value ?: return
        val selectedText = when (selection) {
            is DecisionSelection.Option -> selection.option.label
            is DecisionSelection.Custom -> selection.text.trim()
        }.trim()
        if (selectedText.isBlank()) return

        setPendingDecision(writerMode.value, null)
        submitMessage(selectedText, fromDecisionPrompt = true)
    }

    private fun restoreModeState(checkpoints: List<AgentCheckpointData>): ModeHistoryState {
        val latest = checkpoints.maxByOrNull { it.createdAt }
        val messages = historyFor(checkpoints)
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
                    if (pendingDecision != null) pendingDecision = null
                    restored += cliMessage
                }
                else -> restored += cliMessage
            }
        }

        return ModeHistoryState(
            messages = restored,
            pendingDecision = pendingDecision,
            contextRemainingPercent = ContextCheckpointProperties.readRemainingPercent(latest?.properties),
        )
    }

    private fun historyFor(checkpoints: List<AgentCheckpointData>): List<Message> {
        val latestForAgent = checkpoints.maxByOrNull { it.createdAt } ?: return emptyList()
        return when {
            latestForAgent.isTombstone() -> emptyList()
            latestForAgent.messageHistory.isNotEmpty() -> latestForAgent.messageHistory
            else -> checkpoints.flatMap { it.messageHistory }
        }
    }

    private fun modeState(mode: WriterMode): ModeHistoryState =
        synchronized(modeStateLock) {
            modeHistories[mode] ?: ModeHistoryState()
        }

    private fun setModeState(mode: WriterMode, state: ModeHistoryState) {
        synchronized(modeStateLock) {
            modeHistories[mode] = state
        }
        if (_writerMode.value == mode) {
            applyModeState(mode)
        }
    }

    private fun appendMessage(mode: WriterMode, message: CliMessage) {
        val current = modeState(mode)
        setModeState(
            mode = mode,
            state = current.copy(messages = current.messages + message),
        )
    }

    private fun appendAssistantChunk(mode: WriterMode, text: String) {
        val current = modeState(mode)
        val messages = current.messages
        val updated = if (messages.isEmpty()) {
            messages + CliMessage(data = text, role = CliMessageRole.ASSISTANT)
        } else {
            val mutable = messages.toMutableList()
            val lastIndex = mutable.lastIndex
            val existing = mutable[lastIndex]
            if (existing.role != CliMessageRole.ASSISTANT) {
                mutable += CliMessage(data = text, role = CliMessageRole.ASSISTANT)
            } else {
                mutable[lastIndex] = existing.copy(data = existing.data + text)
            }
            mutable
        }
        setModeState(mode, current.copy(messages = updated))
    }

    private fun setPendingDecision(
        mode: WriterMode,
        decision: DecisionPromptPayload?,
    ) {
        val current = modeState(mode)
        setModeState(mode, current.copy(pendingDecision = decision))
    }

    private fun setContextRemainingPercent(
        mode: WriterMode,
        value: Int?,
    ) {
        val current = modeState(mode)
        setModeState(mode, current.copy(contextRemainingPercent = value))
    }

    private fun applyModeState(mode: WriterMode) {
        val state = modeState(mode)
        _messages.value = state.messages
        _pendingDecision.value = state.pendingDecision
        _contextRemainingPercent.value = state.contextRemainingPercent
    }

    private fun agentIdForMode(
        sessionId: String,
        mode: WriterMode,
    ): String = when (mode) {
        WriterMode.CHAT -> AIProvider.getChatAgentId(sessionId)
        WriterMode.CHAT_STORY -> AIProvider.getStoryAgentId(sessionId)
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
            EntityOptionType.VOLUMES -> navigator.navigate(VolumeListRoute(storyId = storyId))
            EntityOptionType.CHAPTERS -> navigator.navigate(ChapterListRoute(storyId = storyId))
            EntityOptionType.SCENES -> navigator.navigate(SceneListRoute(storyId = storyId))
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

    private suspend fun refreshContextStatus(
        sessionId: String,
        mode: WriterMode,
    ) {
        val latestCheckpoint = storageProvider.getLatestCheckpoint(agentIdForMode(sessionId, mode))
        setContextRemainingPercent(
            mode = mode,
            value = ContextCheckpointProperties.readRemainingPercent(latestCheckpoint?.properties),
        )
    }

    private suspend fun clearPersistedContext(
        sessionId: String,
        mode: WriterMode,
    ) {
        val agentId = agentIdForMode(sessionId, mode)
        val latest = storageProvider.getLatestCheckpoint(agentId)
        val version = (latest?.version?.plus(1) ?: 0L).coerceAtLeast(0L)
        val tombstone = tombstoneCheckpoint(Clock.System.now(), version)
        storageProvider.saveCheckpoint(agentId, tombstone)
    }
}
