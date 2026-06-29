# Use view models and MVI

This guide shows how to drive a screen from a view model: hold state, handle intents, emit one-time
side effects, and collect it all in a composable. It assumes a working app.

View models come with `dispatch-widgets` (which depends on `dispatch-viewmodel`), so no extra
dependency is needed for a typical app.

## Pick a base class

Choose by what the screen needs:

| Need | Base class |
|---|---|
| Observable state only | `StateViewModel<S>` |
| State + intents | `MviViewModel<S, I>` |
| State + one-time effects | `SideEffectViewModel<S, E>` |
| State + intents + effects | `FullMviViewModel<S, I, E>` |

`S` (state), `I` (intent), and `E` (effect) are your own types.

## Define the contract

State is an immutable `data class`; intents and effects are `sealed interface` hierarchies.

```kotlin
data class ChatState(
    val messages: List<String> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
)

sealed interface ChatIntent {
    data class UpdateInput(val text: String) : ChatIntent
    data class Submit(val text: String) : ChatIntent
    data object Clear : ChatIntent
}

sealed interface ChatEffect {
    data object ScrollToBottom : ChatEffect
}
```

## Write the view model

Pass the initial state to the super constructor and override `handleIntent`. Change state with
`updateState { it.copy(...) }`, read the latest snapshot with `currentState`, run coroutines in
`viewModelScope`, and emit effects with `emitSideEffect`.

```kotlin
import com.ead.dispatch.viewmodel.FullMviViewModel

class ChatViewModel(
    private val repository: ChatRepository,
) : FullMviViewModel<ChatState, ChatIntent, ChatEffect>(ChatState()) {

    override suspend fun handleIntent(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.UpdateInput -> updateState { it.copy(input = intent.text) }
            is ChatIntent.Submit -> submit(intent.text)
            ChatIntent.Clear -> updateState { it.copy(messages = emptyList()) }
        }
    }

    private fun submit(text: String) {
        if (currentState.isSending) return
        updateState { it.copy(isSending = true, input = "") }
        viewModelScope.launch {
            val reply = repository.send(text)
            updateState { it.copy(messages = it.messages + text + reply, isSending = false) }
            emitSideEffect(ChatEffect.ScrollToBottom)
        }
    }
}
```

## Obtain it in a screen

Call `viewModel()` to get the view model for the current navigation entry. For a view model with a
no-arg constructor, the no-arg overload is enough; otherwise pass a `factory` (or use
[Koin](use-koin-di.md), which resolves dependencies automatically).

```kotlin
import com.ead.dispatch.viewmodel.viewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel { ChatViewModel(repository) }) {
    // …
}
```

## Collect state and send intents

Collect `state` with `collectAsState`, render from it, and send intents in response to input.

```kotlin
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    Column {
        state.messages.forEach { Text(it) }
        TextField(
            value = state.input,
            onValueChange = { viewModel.sendIntent(ChatIntent.UpdateInput(it)) },
            onSubmit = { viewModel.sendIntent(ChatIntent.Submit(it)) },
        )
    }
}
```

## Consume side effects

Effects are one-time signals — a scroll, a beep, a navigation. Collect them with `collectSideEffect`,
which runs in a `LaunchedEffect`.

```kotlin
import com.ead.dispatch.viewmodel.collectSideEffect

val scrollState = rememberScrollState()
viewModel.sideEffect.collectSideEffect { effect ->
    when (effect) {
        ChatEffect.ScrollToBottom -> scrollState.scrollToBottom()
    }
}
```

## Related

- [View models & MVI reference](../reference/viewmodel.md) — all base classes and helpers.
- [Wire dependency injection with Koin](use-koin-di.md) — resolve view-model dependencies.
- [Add navigation](add-navigation.md) — view models are scoped to navigation entries.
