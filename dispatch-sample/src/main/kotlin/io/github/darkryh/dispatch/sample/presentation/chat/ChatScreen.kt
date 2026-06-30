@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import io.github.darkryh.dispatch.input.Key
import io.github.darkryh.dispatch.input.asKeyEvent
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.modifier.widthIn
import io.github.darkryh.dispatch.navigation.LocalNavigator
import io.github.darkryh.dispatch.navigation.Navigator
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.AppKeyPriority
import io.github.darkryh.dispatch.sample.designsystem.AppScaffold
import io.github.darkryh.dispatch.sample.domain.ChatMessage
import io.github.darkryh.dispatch.sample.domain.MessageAuthor
import io.github.darkryh.dispatch.sample.navigation.HomeRoute
import io.github.darkryh.dispatch.theme.DispatchTheme
import io.github.darkryh.dispatch.viewmodel.collectSideEffect
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.CommandOption
import io.github.darkryh.dispatch.widget.CommandPalette
import io.github.darkryh.dispatch.widget.KeyHint
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Spinner
import io.github.darkryh.dispatch.widget.SpinnerStyle
import io.github.darkryh.dispatch.widget.Surface
import io.github.darkryh.dispatch.widget.SurfaceStyle
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextField
import io.github.darkryh.dispatch.widget.rememberInputHistoryIndexState
import kotlinx.coroutines.delay

/**
 * Simulated streaming chat — the showcase for streaming, cancellation, input history and an inline
 * slash-command palette, rebuilt on [ChatViewModel] (an MVI [io.github.darkryh.dispatch.viewmodel.FullMviViewModel]).
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
