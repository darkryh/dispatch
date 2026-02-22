package com.ead.dispatch.sample.presentation.chat_mode.chat

import ai.koog.agents.snapshot.feature.isTombstone
import ai.koog.agents.snapshot.feature.tombstoneCheckpoint
import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.navigation.toRoute
import com.ead.dispatch.runtime.SavedStateHandle
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.StoryAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatDecisionContext
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.tools.StoryDraftTools
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewPendingRange
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewSnapshot
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewStatus
import com.ead.dispatch.sample.domain.content.ChapterContentStore
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.export.StoryExportRequest
import com.ead.dispatch.sample.domain.export.StoryExportService
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private data class ModeHistoryState(
    val messages: List<CliMessage> = emptyList(),
    val pendingDecision: DecisionPromptPayload? = null,
    val contextRemainingPercent: Int? = null,
    val storyPreviewSnapshot: StoryDraftPreviewSnapshot? = null,
    val storyPreviewFocused: Boolean = false,
    val selectedPageIndex: Int = 0,
    val storyPreviewPageCount: Int = 1,
    val selectedPendingRangeIndex: Int = 0,
    val selectedActionIndex: Int = 0,
    val storyPreviewActiveZone: StoryPreviewFocusZone = StoryPreviewFocusZone.PAGES,
)

/**
 * ViewModel for the chat screen with stub responses.
 */
class ChatViewModel(
    private val commandManager: CommandManager,
    private val sessionManager: SessionManager,
    private val repository: StructuredIndexRepository,
    private val chatAgent: ChatAgent,
    private val storyAgent: StoryAgent,
    private val storyDraftTools: StoryDraftTools,
    private val storyExportService: StoryExportService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val chapterContentStore = ChapterContentStore()

    private val route = savedStateHandle.toRoute<ChatRoute>()
    private val storageProvider = Storage.provider

    // Current session state
    private val _session = MutableStateFlow<Session?>(null)

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    private val _processingElapsedSeconds = MutableStateFlow(0L)
    val processingElapsedSeconds: StateFlow<Long> = _processingElapsedSeconds.asStateFlow()

    private val _messages = MutableStateFlow(emptyList<CliMessage>())
    val messages: StateFlow<List<CliMessage>> = _messages.asStateFlow()
    private val _pendingDecision = MutableStateFlow<DecisionPromptPayload?>(null)
    internal val pendingDecision: StateFlow<DecisionPromptPayload?> = _pendingDecision.asStateFlow()
    private val _contextRemainingPercent = MutableStateFlow<Int?>(null)
    val contextRemainingPercent: StateFlow<Int?> = _contextRemainingPercent.asStateFlow()
    private val _storyPreview = MutableStateFlow<StoryPreviewUiState?>(null)
    val storyPreview: StateFlow<StoryPreviewUiState?> = _storyPreview.asStateFlow()

    // Writer Assistant mode state
    private val _writerMode = MutableStateFlow(WriterMode.CHAT)
    val writerMode: StateFlow<WriterMode> = _writerMode.asStateFlow()

    private val modeStateLock = Any()
    private val modeHistories = WriterMode.entries
        .associateWith { ModeHistoryState() }
        .toMutableMap()

    private var activeStreamJob: Job? = null
    private var processingTimerJob: Job? = null
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
        refreshStoryPreview(sessionId)
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
                if (nextMode == WriterMode.CHAT_STORY) {
                    val activeSessionId = _session.value?.id ?: route.conversationId
                    if (!activeSessionId.isNullOrBlank()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            refreshStoryPreview(activeSessionId)
                        }
                    }
                }
                applyModeState(nextMode)
            }
            is ChatEvent.OnCancelProcessing -> {
                cancelRequested = true
                activeStreamJob?.cancel()
                activeStreamJob = null
                stopProcessingTimer()
            }
            ChatEvent.OnToggleStoryPreviewFocus -> {
                if (_writerMode.value != WriterMode.CHAT_STORY) return
                val current = modeState(WriterMode.CHAT_STORY)
                if (current.storyPreviewSnapshot == null) return
                setModeState(
                    WriterMode.CHAT_STORY,
                    current.copy(
                        storyPreviewFocused = !current.storyPreviewFocused,
                        storyPreviewActiveZone = StoryPreviewFocusZone.PAGES,
                    ),
                )
            }
            ChatEvent.OnStoryPreviewMoveLeft -> moveStoryPreviewHorizontal(-1)
            ChatEvent.OnStoryPreviewMoveRight -> moveStoryPreviewHorizontal(1)
            ChatEvent.OnStoryPreviewMoveUp -> moveStoryPreviewVertical(-1)
            ChatEvent.OnStoryPreviewMoveDown -> moveStoryPreviewVertical(1)
            is ChatEvent.OnStoryPreviewPageCountUpdated -> updateStoryPreviewPageCount(event.pageCount)
            ChatEvent.OnStoryPreviewExecuteSelection -> executeStoryPreviewSelection()
            ChatEvent.OnStoryPreviewApproveShortcut -> executeStoryPreviewAction(actionIndex = 0)
            ChatEvent.OnStoryPreviewRejectShortcut -> executeStoryPreviewAction(actionIndex = 1)
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
            is CommandAction.ExportStory -> {
                runExport(commandAction)
            }
            is CommandAction.ShowError -> {
                appendMessage(
                    mode = _writerMode.value,
                    message = CliMessage(
                        data = commandAction.message,
                        role = CliMessageRole.ASSISTANT,
                    ),
                )
            }
        }
    }

    private fun startProcessingTimer() {
        _isProcessing.value = true
        _processingElapsedSeconds.value = 0L

        processingTimerJob?.cancel()
        val startedAtEpochMillis = Clock.System.now().toEpochMilliseconds()
        processingTimerJob = viewModelScope.launch {
            while (isActive) {
                val nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                val elapsedMillis = (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
                _processingElapsedSeconds.value = elapsedMillis / 1_000L
                delay(200L)
            }
        }
    }

    private fun stopProcessingTimer() {
        processingTimerJob?.cancel()
        processingTimerJob = null
        _isProcessing.value = false
        _processingElapsedSeconds.value = 0L
    }

    private fun runExport(commandAction: CommandAction.ExportStory) {
        if (_isProcessing.value) return
        startProcessingTimer()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = activeSession(firstMessagePreview = "Export")
                val story = repository.getStoriesBySession(session.id).firstOrNull()
                if (story == null) {
                    appendMessage(
                        mode = WriterMode.CHAT_STORY,
                        message = CliMessage(
                            role = CliMessageRole.ASSISTANT,
                            data = "No story exists yet for this session, so export cannot run.",
                        ),
                    )
                    return@launch
                }

                val request = StoryExportRequest(
                    storyId = story.id,
                    scopeType = commandAction.scopeType,
                    scopeId = commandAction.scopeId,
                    format = commandAction.format,
                    outputTarget = commandAction.outputTarget,
                    outputPath = commandAction.outputPath,
                )
                val result = storyExportService.export(request)
                result.onSuccess { export ->
                    val summary = buildString {
                        append("Export complete: ${export.files.size} file(s) written to ${export.rootPath}")
                        if (export.warnings.isNotEmpty()) {
                            append("\nWarnings:")
                            export.warnings.forEach { warning ->
                                append("\n- $warning")
                            }
                        }
                    }
                    appendMessage(
                        mode = WriterMode.CHAT_STORY,
                        message = CliMessage(
                            role = CliMessageRole.ASSISTANT,
                            data = summary,
                        ),
                    )
                }.onFailure { throwable ->
                    appendMessage(
                        mode = WriterMode.CHAT_STORY,
                        message = CliMessage(
                            role = CliMessageRole.ASSISTANT,
                            data = "Export failed: ${throwable.message ?: "Unknown error"}",
                        ),
                    )
                }
            } finally {
                stopProcessingTimer()
            }
        }
    }

    /**
     * Process a submitted message based on the current mode.
     */
    private fun submitMessage(
        text: String,
        fromDecisionPrompt: Boolean,
        decisionContext: ChatDecisionContext? = null,
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
        startProcessingTimer()

        viewModelScope.launch {
            try {
                val session = activeSession(input)

                val response = when (mode) {
                    WriterMode.CHAT -> chatAgent.run(
                        session = session,
                        input = ChatRequest(
                            text = input,
                            storyId = session.id,
                            fromDecisionPrompt = fromDecisionPrompt,
                            decisionContext = decisionContext,
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
                            when (frame) {
                                is StreamFrame.Append -> {
                                    if (frame.text.isEmpty()) {
                                        return@collect
                                    }
                                    appendAssistantChunk(mode, frame.text)
                                }
                                is StreamFrame.ToolCall -> {
                                    val decision = parseDecisionPromptPayload(frame.name, frame.content)
                                    if (decision != null) {
                                        setPendingDecision(mode, decision)
                                    } else {
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
                                    if (mode == WriterMode.CHAT_STORY) {
                                        refreshStoryPreview(session.id)
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
                        stopProcessingTimer()
                    }
                }
                activeStreamJob = job
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                stopProcessingTimer()
            }
        }
    }

    fun onDecisionSelected(selection: DecisionSelection) {
        val pendingDecision = _pendingDecision.value ?: return
        val (selectedText, isCustomSelection) = when (selection) {
            is DecisionSelection.Option -> selection.option.label to false
            is DecisionSelection.Custom -> selection.text.trim() to true
        }
        val normalizedSelection = selectedText.trim()
        if (normalizedSelection.isBlank()) return

        val decisionContext = ChatDecisionContext(
            promptId = pendingDecision.promptId,
            question = pendingDecision.question,
            optionLabels = pendingDecision.options.map { it.label },
            selectedValue = normalizedSelection,
            isCustomSelection = isCustomSelection,
        )

        setPendingDecision(writerMode.value, null)
        submitMessage(
            text = normalizedSelection,
            fromDecisionPrompt = true,
            decisionContext = decisionContext,
        )
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
        _storyPreview.value = if (mode == WriterMode.CHAT_STORY && state.storyPreviewSnapshot != null) {
            StoryPreviewUiState(
                snapshot = state.storyPreviewSnapshot,
                focused = state.storyPreviewFocused,
                selectedPageIndex = state.selectedPageIndex,
                pageCount = state.storyPreviewPageCount,
                selectedPendingRangeIndex = state.selectedPendingRangeIndex,
                selectedActionIndex = state.selectedActionIndex,
                activeZone = state.storyPreviewActiveZone,
            )
        } else {
            null
        }
    }

    private fun canUseStoryPreviewShortcut(): Boolean {
        if (_writerMode.value != WriterMode.CHAT_STORY) return false
        if (_isProcessing.value || _pendingDecision.value != null) return false
        val modeState = modeState(WriterMode.CHAT_STORY)
        val preview = modeState.storyPreviewSnapshot ?: return false
        if (preview.status != StoryDraftPreviewStatus.PENDING) return false
        if (!modeState.storyPreviewFocused) return false
        return !preview.proposalId.isNullOrBlank()
    }

    private fun moveStoryPreviewHorizontal(delta: Int) {
        if (_writerMode.value != WriterMode.CHAT_STORY) return
        val current = modeState(WriterMode.CHAT_STORY)
        val snapshot = current.storyPreviewSnapshot ?: return
        if (!current.storyPreviewFocused) return
        val canUseActions = snapshot.status == StoryDraftPreviewStatus.PENDING && !snapshot.proposalId.isNullOrBlank()
        when (current.storyPreviewActiveZone) {
            StoryPreviewFocusZone.PAGES -> {
                val pageCount = current.storyPreviewPageCount.coerceAtLeast(1)
                val next = (current.selectedPageIndex + delta).coerceIn(0, pageCount - 1)
                if (next != current.selectedPageIndex) {
                    setModeState(
                        WriterMode.CHAT_STORY,
                        current.copy(selectedPageIndex = next),
                    )
                }
            }
            StoryPreviewFocusZone.HUNKS -> {
                val nextZone = if (delta < 0) {
                    StoryPreviewFocusZone.PAGES
                } else if (canUseActions) {
                    StoryPreviewFocusZone.ACTIONS
                } else {
                    StoryPreviewFocusZone.HUNKS
                }
                if (nextZone != current.storyPreviewActiveZone) {
                    setModeState(WriterMode.CHAT_STORY, current.copy(storyPreviewActiveZone = nextZone))
                }
            }
            StoryPreviewFocusZone.ACTIONS -> {
                val nextZone = if (delta < 0) StoryPreviewFocusZone.HUNKS else StoryPreviewFocusZone.ACTIONS
                if (nextZone != current.storyPreviewActiveZone) {
                    setModeState(WriterMode.CHAT_STORY, current.copy(storyPreviewActiveZone = nextZone))
                }
            }
        }
    }

    private fun moveStoryPreviewVertical(delta: Int) {
        if (_writerMode.value != WriterMode.CHAT_STORY) return
        val current = modeState(WriterMode.CHAT_STORY)
        val snapshot = current.storyPreviewSnapshot ?: return
        if (!current.storyPreviewFocused) return
        val canUseActions = snapshot.status == StoryDraftPreviewStatus.PENDING && !snapshot.proposalId.isNullOrBlank()

        when (current.storyPreviewActiveZone) {
            StoryPreviewFocusZone.PAGES -> {
                val targetZone = if (delta > 0) {
                    StoryPreviewFocusZone.HUNKS
                } else if (canUseActions) {
                    StoryPreviewFocusZone.ACTIONS
                } else {
                    StoryPreviewFocusZone.PAGES
                }
                if (targetZone != current.storyPreviewActiveZone) {
                    setModeState(WriterMode.CHAT_STORY, current.copy(storyPreviewActiveZone = targetZone))
                }
            }
            StoryPreviewFocusZone.HUNKS -> {
                if (snapshot.pendingRanges.isEmpty()) return
                val next = (current.selectedPendingRangeIndex + delta)
                    .mod(snapshot.pendingRanges.size)
                setModeState(
                    WriterMode.CHAT_STORY,
                    current.copy(selectedPendingRangeIndex = next),
                )
            }
            StoryPreviewFocusZone.ACTIONS -> {
                if (!canUseStoryPreviewShortcut()) return
                val actionCount = 2
                val next = (current.selectedActionIndex + delta).mod(actionCount)
                setModeState(
                    WriterMode.CHAT_STORY,
                    current.copy(selectedActionIndex = next),
                )
            }
        }
    }

    private fun executeStoryPreviewSelection() {
        if (!canUseStoryPreviewShortcut()) return
        val current = modeState(WriterMode.CHAT_STORY)
        if (current.storyPreviewActiveZone != StoryPreviewFocusZone.ACTIONS) return
        executeStoryPreviewAction(current.selectedActionIndex)
    }

    private fun updateStoryPreviewPageCount(pageCount: Int) {
        if (_writerMode.value != WriterMode.CHAT_STORY) return
        val current = modeState(WriterMode.CHAT_STORY)
        if (current.storyPreviewSnapshot == null) return
        val normalized = pageCount.coerceAtLeast(1)
        if (normalized == current.storyPreviewPageCount &&
            current.selectedPageIndex in 0 until normalized
        ) {
            return
        }
        setModeState(
            WriterMode.CHAT_STORY,
            current.copy(
                storyPreviewPageCount = normalized,
                selectedPageIndex = current.selectedPageIndex.coerceIn(0, normalized - 1),
            ),
        )
    }

    private fun executeStoryPreviewAction(actionIndex: Int) {
        if (!canUseStoryPreviewShortcut()) return
        val snapshot = modeState(WriterMode.CHAT_STORY).storyPreviewSnapshot ?: return
        val proposalId = snapshot.proposalId ?: return
        startProcessingTimer()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = if (actionIndex == 0) {
                    storyDraftTools.applyChapterDraftProposal(
                        storyId = snapshot.storyId,
                        proposalId = proposalId,
                    )
                } else {
                    storyDraftTools.deleteChapterDraftProposal(
                        storyId = snapshot.storyId,
                        proposalId = proposalId,
                    )
                }
                when (result) {
                    is ToolResult.Success<*> -> {
                        val activeSessionId = _session.value?.id ?: route.conversationId
                        if (!activeSessionId.isNullOrBlank()) {
                            refreshStoryPreview(activeSessionId)
                        }
                    }
                    is ToolResult.Failure -> {
                        appendMessage(
                            mode = WriterMode.CHAT_STORY,
                            message = CliMessage(
                                role = CliMessageRole.ASSISTANT,
                                data = "Local preview action failed: ${result.message}",
                            ),
                        )
                    }
                }
            } finally {
                stopProcessingTimer()
            }
        }
    }

    private suspend fun refreshStoryPreview(sessionId: String) {
        val snapshot = loadStoryPreviewSnapshot(sessionId)
        val current = modeState(WriterMode.CHAT_STORY)
        val clampedIndex = if (snapshot == null) {
            0
        } else {
            current.selectedPendingRangeIndex.coerceIn(0, (snapshot.pendingRanges.size - 1).coerceAtLeast(0))
        }
        setModeState(
            WriterMode.CHAT_STORY,
            current.copy(
                storyPreviewSnapshot = snapshot,
                selectedPageIndex = suggestedPageIndexForSnapshot(snapshot),
                storyPreviewPageCount = estimatedPageCountForSnapshot(snapshot),
                selectedPendingRangeIndex = clampedIndex,
                selectedActionIndex = current.selectedActionIndex.coerceIn(0, 1),
                storyPreviewActiveZone = if (snapshot?.status == StoryDraftPreviewStatus.PENDING) {
                    current.storyPreviewActiveZone
                } else {
                    StoryPreviewFocusZone.PAGES
                },
                storyPreviewFocused = if (snapshot == null) false else current.storyPreviewFocused,
            ),
        )
    }

    private fun suggestedPageIndexForSnapshot(snapshot: StoryDraftPreviewSnapshot?): Int {
        snapshot ?: return 0
        val latestRangeStart = snapshot.pendingRanges.maxByOrNull { it.changedAtEpochMillis }?.startLine
        if (latestRangeStart != null) {
            return ((latestRangeStart - 1) / DEFAULT_STORY_PREVIEW_PAGE_ROWS).coerceAtLeast(0)
        }
        val afterLineCount = snapshot.afterText.lineSequence().count().coerceAtLeast(1)
        return ((afterLineCount - 1) / DEFAULT_STORY_PREVIEW_PAGE_ROWS).coerceAtLeast(0)
    }

    private fun estimatedPageCountForSnapshot(snapshot: StoryDraftPreviewSnapshot?): Int {
        snapshot ?: return 1
        val afterLineCount = snapshot.afterText.lineSequence().count().coerceAtLeast(1)
        return ((afterLineCount + DEFAULT_STORY_PREVIEW_PAGE_ROWS - 1) / DEFAULT_STORY_PREVIEW_PAGE_ROWS).coerceAtLeast(1)
    }

    private suspend fun loadStoryPreviewSnapshot(sessionId: String): StoryDraftPreviewSnapshot? {
        val storyId = repository.getStoriesBySession(sessionId).firstOrNull()?.id ?: return null
        val latest = repository.getLatestStoryDraftPreviewByStoryId(storyId) ?: return null
        val chapter = repository.getChapterById(latest.chapterId) ?: return null
        val volume = repository.getVolumeById(chapter.volumeId)
        val pendingRanges = runCatching {
            json.decodeFromString(
                ListSerializer(StoryDraftPreviewPendingRange.serializer()),
                latest.pendingRangesJson,
            )
        }.getOrElse { emptyList() }
        val status = runCatching {
            StoryDraftPreviewStatus.valueOf(latest.status)
        }.getOrDefault(StoryDraftPreviewStatus.APPLIED)
        return StoryDraftPreviewSnapshot(
            storyId = storyId,
            chapterId = latest.chapterId,
            volumeNumber = volume?.number,
            volumeTitle = volume?.title,
            chapterNumber = chapter.number,
            chapterTitle = chapter.title,
            beforeText = chapterContentStore.read(latest.beforeContentRef),
            afterText = chapterContentStore.read(latest.afterContentRef),
            status = status,
            proposalId = latest.proposalId,
            pendingRanges = pendingRanges,
            createdAtEpochMillis = latest.createdAt,
            updatedAtEpochMillis = latest.updatedAt,
        )
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
        if (mode == WriterMode.CHAT_STORY) {
            val storyId = repository.getStoriesBySession(sessionId).firstOrNull()?.id
            if (storyId != null) {
                repository.deleteStoryDraftPreviewStateByStoryId(storyId)
            }
            refreshStoryPreview(sessionId)
        }
    }

    private companion object {
        const val DEFAULT_STORY_PREVIEW_PAGE_ROWS = 22
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    }
}
