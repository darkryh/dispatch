package com.ead.dispatch.sample.presentation.chat_mode.chat

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.inject
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.runtime.*
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewStatus
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.chat_mode.chat.components.*
import com.ead.dispatch.sample.presentation.chat_mode.chat.event.ChatEvent
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.remember
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.*
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import kotlinx.datetime.Clock

@Dispatchable
fun ChatScreen() {
    val viewModel = viewModel<ChatViewModel>()
    val commandManager by inject<CommandManager>()

    val inputText by viewModel.inputText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingElapsedSeconds by viewModel.processingElapsedSeconds.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val pendingDecision by viewModel.pendingDecision.collectAsState()
    val contextRemainingPercent by viewModel.contextRemainingPercent.collectAsState()
    val writerMode by viewModel.writerMode.collectAsState()
    val storyPreview by viewModel.storyPreview.collectAsState()

    val theme = LocalTheme.current
    val navigator = LocalNavigator.current
    val terminalWidth = LocalTerminalWidth.current
    val terminalHeight = LocalTerminalHeight.current

    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val previewFocused = storyPreview?.focused == true

    DisposableEffect("${writerMode.name}|$previewFocused|$isProcessing") {
        val dispose = keyboardInterceptor.register { event ->
            when {
                event.key == "Tab" && event.shift -> {
                    viewModel.onEvent(ChatEvent.OnChatModeChanged)
                    true
                }
                (event.key == "Escape" || event.key == "Esc") && isProcessing -> {
                    viewModel.onEvent(ChatEvent.OnCancelProcessing)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && event.ctrl && event.key.equals("p", ignoreCase = true) -> {
                    viewModel.onEvent(ChatEvent.OnToggleStoryPreviewFocus)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && (event.key == "ArrowLeft" || event.key == "Left") -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewMoveLeft)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && (event.key == "ArrowRight" || event.key == "Right") -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewMoveRight)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && (event.key == "ArrowDown" || event.key == "Down") -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewMoveDown)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && (event.key == "ArrowUp" || event.key == "Up") -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewMoveUp)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && event.key == "Enter" -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewExecuteSelection)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && event.key.equals("a", ignoreCase = true) && !event.ctrl && !event.alt -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewApproveShortcut)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && event.key.equals("r", ignoreCase = true) && !event.ctrl && !event.alt -> {
                    viewModel.onEvent(ChatEvent.OnStoryPreviewRejectShortcut)
                    true
                }
                writerMode == WriterMode.CHAT_STORY && previewFocused && (event.key == "Escape" || event.key == "Esc") -> {
                    viewModel.onEvent(ChatEvent.OnToggleStoryPreviewFocus)
                    true
                }
                else -> false
            }
        }
        onDispose { dispose() }
    }

    val commands = remember(writerMode) {
        commandManager.commandsFor(writerMode).map { command ->
            CommandOption(
                label = command.label,
                description = command.description,
                data = command.label,
            )
        }
    }

    val historyItems = remember(messages) {
        messages.filter { it.role == CliMessageRole.USER }.map { it.data }
    }
    val historyIndexState = rememberInputHistoryIndexState()

    val (placeholder, icon) = when (writerMode) {
        WriterMode.CHAT -> "Describe your story (characters, genre, setting, plot)..." to "> "
        WriterMode.CHAT_STORY -> "What happens next in your story?" to "✦ "
    }

    val commandPaletteState = rememberCommandPaletteState<String>()
    val decisionPromptStyles = remember(theme) {
        buildDecisionPromptStyles(theme)
    }

    val inlineStoryPreviewRows = when {
        terminalWidth >= 170 -> (terminalHeight / 3).coerceIn(10, 18)
        terminalWidth >= 140 -> (terminalHeight / 3).coerceIn(10, 16)
        terminalWidth >= 110 -> (terminalHeight / 4).coerceIn(9, 14)
        else -> 9
    }

    ChatConversationColumn(
        modifier = Modifier.fillMaxWidth(),
        writerMode = writerMode,
        messages = messages,
        pendingDecision = pendingDecision,
        isProcessing = isProcessing,
        processingElapsedSeconds = processingElapsedSeconds,
        inputText = inputText,
        placeholder = placeholder,
        icon = icon,
        navigator = navigator,
        historyItems = historyItems,
        historyIndexState = historyIndexState,
        commands = commands,
        commandPaletteState = commandPaletteState,
        decisionPromptStyles = decisionPromptStyles,
        theme = theme,
        contextRemainingPercent = contextRemainingPercent,
        onEvent = viewModel::onEvent,
        onDecisionSelected = viewModel::onDecisionSelected,
        inlineStoryPreview = storyPreview,
        inlineStoryPreviewRows = inlineStoryPreviewRows,
    )
}

@Dispatchable
private fun ChatConversationColumn(
    modifier: Modifier,
    writerMode: WriterMode,
    messages: List<CliMessage>,
    pendingDecision: DecisionPromptPayload?,
    isProcessing: Boolean,
    processingElapsedSeconds: Long,
    inputText: String,
    placeholder: String,
    icon: String,
    navigator: Navigator,
    historyItems: List<String>,
    historyIndexState: InputHistoryIndexState,
    commands: List<CommandOption<String>>,
    commandPaletteState: CommandPaletteState<String>,
    decisionPromptStyles: DecisionPromptTextStyles,
    theme: DispatchTheme,
    contextRemainingPercent: Int?,
    onEvent: (ChatEvent) -> Unit,
    onDecisionSelected: (DecisionSelection) -> Unit,
    inlineStoryPreview: StoryPreviewUiState?,
    inlineStoryPreviewRows: Int,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { ChatHeader() }
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                val quickJump = when (writerMode) {
                    WriterMode.CHAT -> buildQuickJump(
                        includeStoryChat = true,
                        types = EntityOptionType.chatQuickJumpTypes,
                    )

                    WriterMode.CHAT_STORY -> buildQuickJump(
                        includeStoryChat = false,
                        types = listOf(
                            EntityOptionType.VOLUMES,
                            EntityOptionType.CHAPTERS,
                            EntityOptionType.SCENES,
                        )
                    )
                }
                Text(
                    text = "Quick jump: $quickJump",
                    style = theme.muted,
                )
            }
        }
        item { Spacer(Modifier.height(1)) }

        items(messages) { message ->
            ChatMessage(message = message)
        }

        if (inlineStoryPreview != null) {
            item { Spacer(Modifier.height(1)) }
            item {
                StoryPreviewPanel(
                    preview = inlineStoryPreview,
                    maxVisibleRows = inlineStoryPreviewRows,
                    compact = true,
                    onEvent = onEvent,
                )
            }
        }

        pendingDecision?.let { decision ->
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.width(2))
                    DecisionPrompt(
                        question = decision.question,
                        options = decision.options,
                        placeholder = decision.placeholder,
                        textStyles = decisionPromptStyles,
                        onSubmit = onDecisionSelected,
                    )
                    Spacer(modifier = Modifier.width(2))
                }
            }
        }

        item { Spacer(Modifier.height(1)) }
        item {
            ChatProgressAnimation(
                isProcessing = isProcessing,
                processingElapsedSeconds = processingElapsedSeconds,
            )
        }
        item {
            ChatInputTextField(
                modifier = Modifier.fillMaxWidth(),
                value = inputText,
                onValueChange = { text -> onEvent(ChatEvent.OnTextChanged(text)) },
                icon = icon,
                placeholder = placeholder,
                textStyle = rgb("#FFFFFF"),
                placeholderStyle = rgb("#82858A"),
                enabled = !isProcessing && pendingDecision == null,
                showCursor = !isProcessing && pendingDecision == null,
                onSubmit = { text -> onEvent(ChatEvent.OnSubmitMessage(navigator, text)) },
                historyItems = historyItems,
                historyIndexState = historyIndexState,
            )
        }
        item {
            ChatCommandPalette(
                modifier = Modifier.fillMaxWidth(),
                state = commandPaletteState,
                options = commands,
                inputValue = inputText,
                onOptionSelected = { option ->
                    onEvent(ChatEvent.OnSubmitMessage(navigator, "/${option.data}"))
                },
                onInputTransform = { newInput -> onEvent(ChatEvent.OnTextChanged(newInput)) },
                textStyles = CommandPaletteTextStyles(
                    prefix = theme.muted,
                    selectedPrefix = theme.accent + TextStyle(bold = false),
                    label = theme.primary,
                    selectedLabel = theme.accent + TextStyle(bold = false),
                    description = theme.muted,
                    selectedDescription = theme.muted,
                )
            )
        }
        item {
            ChatStatusBar(
                isCommandPaletteVisible = commandPaletteState.isVisible,
                writerMode = writerMode,
                contextRemainingPercent = contextRemainingPercent,
            )
        }
    }
}

@Dispatchable
private fun StoryPreviewPanel(
    preview: StoryPreviewUiState?,
    maxVisibleRows: Int,
    compact: Boolean,
    onEvent: (ChatEvent) -> Unit,
) {
    if (preview == null) return
    val snapshot = preview.snapshot
    val now = Clock.System.now().toEpochMilliseconds()

    val pendingRanges = snapshot.pendingRanges.map { range ->
        PendingLineRange(
            startLine = range.startLine,
            endLine = range.endLine,
            changedAtEpochMillis = range.changedAtEpochMillis,
        )
    }

    val approval = if (pendingRanges.isEmpty()) null else FileChangeApprovalConfig(
        pendingRanges = pendingRanges,
        expiryMillis = FileChangeApprovalConfig.DEFAULT_EXPIRY_MILLIS,
    )

    val selectedLabel = if (pendingRanges.isEmpty()) {
        "range: none"
    } else {
        val safeIndex = preview.selectedPendingRangeIndex.coerceIn(0, pendingRanges.lastIndex)
        "range ${safeIndex + 1}/${pendingRanges.size}"
    }
    val scopeLabel = buildString {
        if (snapshot.volumeNumber != null) {
            append("Volume ")
            append(snapshot.volumeNumber)
            snapshot.volumeTitle?.takeIf { it.isNotBlank() }?.let {
                append(" — ")
                append(it)
            }
        }
        if (snapshot.chapterNumber != null) {
            if (isNotEmpty()) append(" · ")
            append("Chapter ")
            append(snapshot.chapterNumber)
            if (snapshot.chapterTitle.isNotBlank()) {
                append(" — ")
                append(snapshot.chapterTitle)
            }
        } else if (snapshot.chapterTitle.isNotBlank()) {
            if (isNotEmpty()) append(" · ")
            append(snapshot.chapterTitle)
        }
    }
    val zoneLabel = preview.activeZone.name.lowercase()
    val actions = listOf(
        DiffReviewAction("Approve Proposal"),
        DiffReviewAction("Reject Proposal"),
    )
    val showActions = snapshot.status == StoryDraftPreviewStatus.PENDING && !snapshot.proposalId.isNullOrBlank()

    val previewState = FileChangePreviewState(
        filePath = "chapter:${snapshot.chapterTitle}",
        fileType = PreviewFileType.MARKDOWN,
        beforeText = snapshot.beforeText,
        afterText = snapshot.afterText,
        approval = approval,
        nowEpochMillis = now,
        focusMode = ChangeFocusMode.FULL,
        contextLines = 3,
        showCollapsedUnchanged = true,
        selectedHunkIndex = preview.selectedPendingRangeIndex,
        pageIndex = preview.selectedPageIndex,
        pageSizeRows = maxVisibleRows.coerceAtLeast(1),
    )

    val panelState = DiffReviewPanelState(
        title = if (preview.focused) "Preview (focused)" else "Preview",
        subtitle = scopeLabel.takeIf { it.isNotBlank() },
        statusLine = "status: ${snapshot.status.name.lowercase()} · zone: $zoneLabel · $selectedLabel · ctrl+p focus",
        previewState = previewState,
        pagesFocused = preview.focused && preview.activeZone == StoryPreviewFocusZone.PAGES,
        actions = if (showActions) actions else emptyList(),
        selectedActionIndex = preview.selectedActionIndex,
        actionsFocused = preview.focused && preview.activeZone == StoryPreviewFocusZone.ACTIONS,
    )

    DiffReviewPanel(
        state = panelState,
        maxVisibleRows = maxVisibleRows,
        compact = compact,
        onPageCountResolved = { count -> onEvent(ChatEvent.OnStoryPreviewPageCountUpdated(count)) },
        actionsKeyHints = listOf(
            KeyHint("Ctrl+P", "focus"),
            KeyHint("←/→", "page or zone"),
            KeyHint("↑/↓", "range/action zone"),
            KeyHint("Enter", "select"),
            KeyHint("Esc", "input"),
        ),
        defaultKeyHints = listOf(
            KeyHint("Ctrl+P", "focus"),
            KeyHint("←/→", "page or zone"),
            KeyHint("↑/↓", "range zone"),
            KeyHint("Esc", "input"),
        ),
    )
}

private fun buildQuickJump(
    includeStoryChat: Boolean,
    types: List<EntityOptionType>,
): String {
    val labels = buildList {
        if (includeStoryChat) add("story-chat")
        addAll(types.map { it.id })
    }
    return labels.joinToString(separator = ", ") { "/$it" }
}

private fun buildDecisionPromptStyles(theme: DispatchTheme): DecisionPromptTextStyles =
    DecisionPromptTextStyles(
        question = theme.primary,
        option = DECISION_OPTION_UNSELECTED_COLOR,
        selectedOption = theme.accent + TextStyle(bold = false),
        prefix = DECISION_OPTION_UNSELECTED_COLOR,
        selectedPrefix = theme.accent + TextStyle(bold = false),
        placeholder = theme.muted,
        customText = theme.primary,
    )

private val DECISION_OPTION_UNSELECTED_COLOR = rgb("#FFFFFF")
