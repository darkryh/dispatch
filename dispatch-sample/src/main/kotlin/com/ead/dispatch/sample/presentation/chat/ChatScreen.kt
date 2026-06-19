@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.navigation.Navigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.domain.ChatMessage
import com.ead.dispatch.sample.domain.MessageAuthor
import com.ead.dispatch.sample.navigation.ComponentsRoute
import com.ead.dispatch.sample.navigation.HomeRoute
import com.ead.dispatch.sample.presentation.common.SampleScaffold
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.Button
import com.ead.dispatch.widget.ButtonRow
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.InputHistoryIndexState
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.rememberInputHistoryIndexState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val isStreaming by viewModel.streamingState.collectAsState()
    val history = messages.filter { it.author == MessageAuthor.USER }.map { it.text }
    val historyState = rememberInputHistoryIndexState()

    // Esc stops an in-flight streaming response (higher priority than Esc-as-back).
    DisposableEffect(keyboardInterceptor, isStreaming) {
        val dispose =
            keyboardInterceptor.register(priority = 100) { event ->
                if (isStreaming && (event.key == "Escape" || event.key == "Esc")) {
                    viewModel.cancel()
                    true
                } else {
                    false
                }
            }
        onDispose(dispose)
    }

    val status =
        if (isStreaming) {
            "Streaming… press Esc to stop. Type / for commands."
        } else {
            "Type a message, or / for commands. Ctrl+P jumps anywhere."
        }

    SampleScaffold(
        title = "Simulated streaming chat",
        subtitle = status,
        escGoesBack = false,
        hints =
            listOf(
                KeyHint("/", "commands"),
                KeyHint("Enter", "send"),
                KeyHint("Esc", "stop stream"),
            ),
        footer = {
            ChatComposer(
                input = input,
                isStreaming = isStreaming,
                history = history,
                historyState = historyState,
                navigator = navigator,
                onInputChanged = viewModel::updateInput,
                onSubmit = viewModel::submit,
                onCancel = viewModel::cancel,
                onClear = viewModel::clearConversation,
            )
        },
    ) {
        ChatMessages(messages, theme)
    }
}

@Composable
private fun ChatComposer(
    input: String,
    isStreaming: Boolean,
    history: List<String>,
    historyState: InputHistoryIndexState,
    navigator: Navigator,
    onInputChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
) {
    Spacer(Modifier.height(1))
    Background(style = BackgroundStyle.Fill(rgb("#303846"))) {
        InputTextField(
            value = input,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            icon = "> ",
            placeholder = "Ask the local response simulator… (try \"/\")",
            enabled = !isStreaming,
            showCursor = !isStreaming,
            maxLines = 4,
            onSubmit = onSubmit,
            historyItems = history,
            historyIndexState = historyState,
        )
    }

    // Slash-command palette: opens when the input begins with '/'. Arrow keys move, Enter runs.
    val commands =
        listOf(
            CommandOption(label = "clear", description = "Clear the conversation", data = "clear"),
            CommandOption(label = "cancel", description = "Stop the streaming response", data = "cancel", enabled = isStreaming),
            CommandOption(label = "home", description = "Go to the main menu", data = "home"),
            CommandOption(label = "components", description = "Open the widget galleries", data = "components"),
        )
    CommandPalette(
        modifier = Modifier.fillMaxWidth(),
        options = commands,
        inputValue = input,
        onOptionSelected = { option ->
            when (option.data) {
                "clear" -> onClear()
                "cancel" -> onCancel()
                "home" -> navigator.navigate(HomeRoute())
                "components" -> navigator.navigate(ComponentsRoute())
            }
        },
        onInputTransform = onInputChanged,
    )

    Spacer(Modifier.height(1))
    ButtonRow {
        Button("Back", onClick = { navigator.popBackStack() })
        Button("Cancel stream", onClick = onCancel, enabled = isStreaming)
        Button("Clear", onClick = onClear)
    }
}

@Composable
private fun ChatMessages(
    messages: List<ChatMessage>,
    theme: DispatchTheme,
) {
    // Render every message as flowing content (NOT a bounded LazyColumn): the runtime measures the
    // root unbounded and commits everything above the active area into the terminal's native
    // scrollback, so the full history stays scrollable with the mouse wheel. Finalized messages have
    // stable keys, so Compose skips re-rendering them as new ones stream in.
    Column(modifier = Modifier.fillMaxWidth()) {
        messages.forEach { message ->
            key(message.id) {
                val author = if (message.author == MessageAuthor.USER) "You" else "Sample"
                val suffix = if (message.isStreaming) " [streaming]" else ""
                val style =
                    if (message.author == MessageAuthor.USER) {
                        theme.userMessage
                    } else {
                        theme.assistantMessage
                    }
                Panel(modifier = Modifier.fillMaxWidth(), title = author + suffix) {
                    Column {
                        Text(
                            text = message.text.ifEmpty { "Waiting for the first chunk..." },
                            style = style,
                        )
                    }
                }
                Spacer(Modifier.height(1))
            }
        }
    }
}
