package com.ead.dispatch.sample.presentation.chat

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.koin.inject
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.domain.CommandManager
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.sample.presentation.chat.components.*
import com.ead.dispatch.sample.presentation.chat.event.ChatEvent
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.remember
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.*
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Main chat screen composable.
 *
 * Demonstrates proper usage patterns:
 * - Uses [viewModel] for state management
 * - Uses [collectAsState] for reactive state observation
 * - Uses [InputTextField] for automatic keyboard handling
 * - Uses extracted components for better organization
 */
@Dispatchable
fun ChatScreen(backStack: NavBackStack<NavKey>) {
    // ViewModel provides state management
    val viewModel = viewModel<ChatViewModel>()
    val commandManager by inject<CommandManager>()

    // Collect state reactively using property delegation
    val inputText by viewModel.inputText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()

    val messages by viewModel.messages.collectAsState()

    val writerMode by viewModel.writerMode.collectAsState()
    val theme = LocalTheme.current

    // Register Shift+Tab handler for mode switching without replacing InputTextField handlers.
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    DisposableEffect(Unit) {
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
                else -> false
            }
        }
        onDispose { dispose() }
    }

    // Define available commands for the command palette
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

    // Mode-specific placeholder and icon
    val (placeholder, icon) = when (writerMode) {
        WriterMode.CHAT -> "Describe your story (characters, genre, setting, plot)..." to "> "
        WriterMode.CHAT_STORY -> "What happens next in your story?" to "✦ "
    }

    val commandPaletteState = rememberCommandPaletteState<String>()

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item {
            ChatHeader()
        }
        item {
            Spacer(Modifier.height(1))
        }
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
        item {
            Spacer(Modifier.height(1))
        }
        items(messages) { message ->
            ChatMessage(message = message)
        }
        item {
            Spacer(Modifier.height(1))
        }
        item {
            ChatProgressAnimation(isProcessing)
        }
        item {
            ChatInputTextField(
                modifier = Modifier.fillMaxWidth(),
                value = inputText,
                onValueChange = { text -> viewModel.onEvent(event = ChatEvent.OnTextChanged(text)) },
                icon = icon,
                placeholder = placeholder,
                textStyle = rgb("#FFFFFF"),
                placeholderStyle = rgb("#82858A"),
                enabled = !isProcessing,
                showCursor = !isProcessing,
                onSubmit = { text -> viewModel.onEvent(event = ChatEvent.OnSubmitMessage(backStack, text)) },
                historyItems = historyItems,
                historyIndexState = historyIndexState,
            )
        }

        // Command Palette - shown below InputTextField when triggered by '/'
        item {
            ChatCommandPalette(
                modifier = Modifier.fillMaxWidth(),
                state = commandPaletteState,
                options = commands,
                inputValue = inputText,
                onOptionSelected = { option ->  viewModel.onEvent(ChatEvent.OnSubmitMessage(backStack, "/${option.data}")) },
                onInputTransform = { newInput -> viewModel.onEvent(ChatEvent.OnTextChanged(newInput)) },
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
            )
        }
    }
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
