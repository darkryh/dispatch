@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.widthIn
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.designsystem.AppKeyPriority
import com.ead.dispatch.sample.designsystem.AppScaffold
import com.ead.dispatch.sample.domain.ChatMessage
import com.ead.dispatch.sample.domain.MessageAuthor
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.viewmodel.collectSideEffect
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Spinner
import com.ead.dispatch.widget.SpinnerStyle
import com.ead.dispatch.widget.Surface
import com.ead.dispatch.widget.SurfaceStyle
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextField
import com.ead.dispatch.widget.rememberInputHistoryIndexState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import kotlinx.coroutines.delay

/**
 * Simulated streaming chat — the showcase for streaming, cancellation, input history and an inline
 * slash-command palette, rebuilt on [ChatViewModel] (an MVI [com.ead.dispatch.viewmodel.FullMviViewModel]).
 *
 * Cancellation is **one keypress**: while a reply streams, `Esc` stops it (and is consumed at
 * [AppKeyPriority.CHAT_CANCEL]); when idle, `Esc` falls through to the scaffold and goes back. There
 * is deliberately no "cancel" button — the key is the affordance, surfaced prominently in the hint bar.
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    val interceptor = LocalKeyboardInterceptor.current
    val state by viewModel.state.collectAsState()

    viewModel.sideEffect.collectSideEffect {
        // The transcript commits finished messages into the terminal's native scrollback, so the
        // newest content is already at the bottom; this hook exists to show the effect pattern.
    }

    // Esc cancels an in-flight stream and is consumed only while streaming; otherwise it falls
    // through to the scaffold's Esc-back. Higher priority than Esc-back, lower than the global palette.
    DisposableEffect(interceptor) {
        val dispose =
            interceptor.register(priority = AppKeyPriority.CHAT_CANCEL) { rawEvent ->
                if (rawEvent.asKeyEvent().key == Key.Escape && viewModel.currentState.isStreaming) {
                    viewModel.sendIntent(ChatIntent.Cancel)
                    true
                } else {
                    false
                }
            }
        onDispose { dispose() }
    }

    // A frame counter that ticks only while streaming, to animate the inline spinner.
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(state.isStreaming) {
        while (state.isStreaming) {
            delay(SPINNER_TICK_MILLIS)
            frame++
        }
    }

    val subtitle =
        if (state.isStreaming) {
            "Streaming… press Esc to stop. Type / for commands."
        } else {
            "Type a message, or / for commands. Ctrl+P jumps anywhere."
        }

    AppScaffold(
        title = "Simulated streaming chat",
        subtitle = subtitle,
        hints =
            listOf(
                KeyHint("Enter", "send"),
                KeyHint("/", "commands"),
                KeyHint("↑/↓", "history"),
                KeyHint("Esc", if (state.isStreaming) "stop streaming" else "back"),
            ),
        footer = {
            ChatComposer(
                state = state,
                navigator = navigator,
                onIntent = viewModel::sendIntent,
            )
        },
    ) {
        ChatTranscript(messages = state.messages, frame = frame, theme = theme)
    }
}

/**
 * The conversation transcript. Rendered as an unbounded [Column] (NOT a bounded `LazyColumn`): the
 * runtime measures the root unbounded and commits everything above the active area into the
 * terminal's native scrollback, so the whole history stays scrollable with the mouse wheel.
 * Finalized messages keep stable keys, so Compose skips re-rendering them as new chunks arrive.
 */
@Composable
private fun ChatTranscript(
    messages: List<ChatMessage>,
    frame: Int,
    theme: DispatchTheme,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        messages.forEach { message ->
            key(message.id) {
                if (message.author == MessageAuthor.USER) {
                    UserMessage(message, theme)
                } else {
                    AssistantMessage(message, frame, theme)
                }
                Spacer(Modifier.height(1))
            }
        }
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    theme: DispatchTheme,
) {
    // A content-sized rounded bubble, hugged to the right edge by a flexible spacer. The width is
    // capped so long messages wrap instead of overflowing, while short ones stay small.
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1f))
        Panel(modifier = Modifier.widthIn(max = bubbleMaxWidth()), title = "You", titleStyle = theme.accent) {
            Text(message.text, style = theme.primary)
        }
    }
}

@Composable
private fun AssistantMessage(
    message: ChatMessage,
    frame: Int,
    theme: DispatchTheme,
) {
    // Mirror of [UserMessage]: a content-sized rounded bubble on the left, with the live spinner
    // floating above it while the reply streams in.
    Row(modifier = Modifier.fillMaxWidth()) {
        Column {
            if (message.isStreaming) {
                Row {
                    Spinner(frame = frame, style = SpinnerStyle.Dots, textStyle = theme.muted)
                    Text(" streaming…", style = theme.muted)
                }
            }
            Panel(modifier = Modifier.widthIn(max = bubbleMaxWidth()), title = "Sample", titleStyle = theme.info) {
                Text(message.text.ifEmpty { "Waiting for the first chunk…" }, style = theme.info)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Cap a chat bubble at ~72% of the terminal so long text wraps inside it rather than overflowing. */
@Composable
private fun bubbleMaxWidth(): Int = (LocalTerminalWidth.current * 72 / 100).coerceAtLeast(20)

@Composable
private fun ChatComposer(
    state: ChatState,
    navigator: Navigator,
    onIntent: (ChatIntent) -> Unit,
) {
    val history = state.messages.filter { it.author == MessageAuthor.USER }.map { it.text }
    val historyState = rememberInputHistoryIndexState()

    Spacer(Modifier.height(1))
    Surface(style = SurfaceStyle.fill(rgb("#303846"))) {
        TextField(
            value = state.input,
            onValueChange = { onIntent(ChatIntent.UpdateInput(it)) },
            modifier = Modifier.fillMaxWidth(),
            icon = "› ",
            placeholder = "Ask the local response simulator…  (try \"/\")",
            enabled = !state.isStreaming,
            showCursor = !state.isStreaming,
            maxLines = 4,
            onSubmit = { onIntent(ChatIntent.Submit(it)) },
            historyItems = history,
            historyIndexState = historyState,
        )
    }

    // Slash-command palette: opens when the input begins with '/'. Arrow keys move, Enter runs.
    val commands =
        listOf(
            CommandOption(label = "clear", description = "Clear the conversation", data = "clear"),
            CommandOption(label = "cancel", description = "Stop the streaming response", data = "cancel", enabled = state.isStreaming),
            CommandOption(label = "home", description = "Go to the main menu", data = "home"),
        )
    CommandPalette(
        modifier = Modifier.fillMaxWidth(),
        options = commands,
        inputValue = state.input,
        onOptionSelected = { option ->
            when (option.data) {
                "clear" -> onIntent(ChatIntent.Clear)
                "cancel" -> onIntent(ChatIntent.Cancel)
                "home" -> navigator.navigate(HomeRoute())
            }
        },
        onInputTransform = { onIntent(ChatIntent.UpdateInput(it)) },
    )
}

private const val SPINNER_TICK_MILLIS = 120L
