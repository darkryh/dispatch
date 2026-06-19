@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.layout.TerminalScreen
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.domain.ChatMessage
import com.ead.dispatch.sample.domain.MessageAuthor
import com.ead.dispatch.theme.DispatchTheme
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.Button
import com.ead.dispatch.widget.ButtonRow
import com.ead.dispatch.widget.InputHistoryIndexState
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.rememberInputHistoryIndexState

private data class ChatUiState(
    val messages: List<ChatMessage>,
    val input: String,
    val isStreaming: Boolean,
    val history: List<String>,
    val historyState: InputHistoryIndexState,
)

private data class ChatActions(
    val onInputChanged: (String) -> Unit,
    val onSubmit: (String) -> Unit,
    val onBack: () -> Unit,
    val onCancel: () -> Unit,
    val onClear: () -> Unit,
)

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val isStreaming by viewModel.streamingState.collectAsState()
    val history = messages.filter { it.author == MessageAuthor.USER }.map { it.text }
    val historyState = rememberInputHistoryIndexState()
    val keyboardInterceptor = LocalKeyboardInterceptor.current

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

    val state =
        ChatUiState(
            messages = messages,
            input = input,
            isStreaming = isStreaming,
            history = history,
            historyState = historyState,
        )
    val actions =
        ChatActions(
            onInputChanged = viewModel::updateInput,
            onSubmit = viewModel::submit,
            onBack = { navigator.popBackStack() },
            onCancel = viewModel::cancel,
            onClear = viewModel::clearConversation,
        )
    ChatContent(
        state = state,
        theme = theme,
        actions = actions,
    )
}

@Composable
private fun ChatContent(
    state: ChatUiState,
    theme: DispatchTheme,
    actions: ChatActions,
) {
    TerminalScreen(
        header = { ChatHeader(state.isStreaming, theme) },
        footer = {
            ChatFooter(
                state = state,
                actions = actions,
            )
        },
    ) {
        ChatMessages(state.messages, theme)
    }
}

@Composable
private fun ChatHeader(
    isStreaming: Boolean,
    theme: DispatchTheme,
) {
    Text("Simulated streaming chat", style = theme.primary)
    val status =
        if (isStreaming) {
            "Receiving irregular local chunks..."
        } else {
            "Ready - no network or agent runtime is used."
        }
    Text(status, style = if (isStreaming) theme.warning else theme.muted)
    Spacer(Modifier.height(1))
}

@Composable
private fun ChatFooter(
    state: ChatUiState,
    actions: ChatActions,
) {
    Spacer(Modifier.height(1))
    InputTextField(
        value = state.input,
        onValueChange = actions.onInputChanged,
        modifier = Modifier.fillMaxWidth(),
        icon = "> ",
        placeholder = "Ask the local response simulator...",
        enabled = !state.isStreaming,
        showCursor = !state.isStreaming,
        maxLines = 4,
        onSubmit = actions.onSubmit,
        historyItems = state.history,
        historyIndexState = state.historyState,
    )
    Spacer(Modifier.height(1))
    ButtonRow {
        Button("Back", onClick = actions.onBack)
        Button("Cancel stream", onClick = actions.onCancel, enabled = state.isStreaming)
        Button("Clear", onClick = actions.onClear)
    }
    KeyHintBar(
        hints =
            listOf(
                KeyHint("Up/Down", "history or vertical cursor"),
                KeyHint("Enter", "send"),
                KeyHint("Esc", "cancel stream"),
                KeyHint("Tab", "change focus"),
            ),
    )
}

@Composable
private fun ChatMessages(
    messages: List<ChatMessage>,
    theme: DispatchTheme,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth(), stickToEnd = true) {
        items(messages, key = { it.id }) { message ->
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
