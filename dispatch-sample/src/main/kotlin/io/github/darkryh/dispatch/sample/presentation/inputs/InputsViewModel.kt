package io.github.darkryh.dispatch.sample.presentation.inputs

import io.github.darkryh.dispatch.viewmodel.MviViewModel

/**
 * Immutable snapshot rendered by the Inputs playground. [name], [password] and [notes] mirror the
 * three live text fields; [submitted] is the running list of names committed with Enter and doubles
 * as the up/down history source for the name field.
 */
data class InputsState(
    val name: String = "",
    val password: String = "",
    val notes: String = "",
    val submitted: List<String> = emptyList(),
)

/**
 * Intents emitted by the Inputs screen. Every keystroke flows through an `Update*` intent so the
 * fields stay a pure function of [InputsState]; [Submit] commits the name and [ClearSubmitted]
 * empties the history.
 */
sealed interface InputsIntent {
    /** The name field text changed. */
    data class UpdateName(
        val text: String,
    ) : InputsIntent

    /** The password field text changed. */
    data class UpdatePassword(
        val text: String,
    ) : InputsIntent

    /** The notes field text changed. */
    data class UpdateNotes(
        val text: String,
    ) : InputsIntent

    /** Commit the current name into [InputsState.submitted] and clear the name field (Enter). */
    data object Submit : InputsIntent

    /** Drop every previously submitted name. */
    data object ClearSubmitted : InputsIntent
}

/**
 * Drives the Inputs playground. Unlike the other category screens it does **not** use
 * [io.github.darkryh.dispatch.sample.designsystem.PlaygroundController]: the real text fields own the keyboard
 * (typing, cursor arrows, Tab focus traversal and ↑/↓ history), so this view-model only mirrors their
 * edits into state via [updateState] and reacts to [InputsIntent.Submit].
 */
class InputsViewModel : MviViewModel<InputsState, InputsIntent>(InputsState()) {
    override suspend fun handleIntent(intent: InputsIntent) {
        when (intent) {
            is InputsIntent.UpdateName -> updateState { it.copy(name = intent.text) }
            is InputsIntent.UpdatePassword -> updateState { it.copy(password = intent.text) }
            is InputsIntent.UpdateNotes -> updateState { it.copy(notes = intent.text) }
            InputsIntent.Submit ->
                updateState { state ->
                    val trimmed = state.name.trim()
                    if (trimmed.isEmpty()) {
                        state
                    } else {
                        state.copy(name = "", submitted = state.submitted + trimmed)
                    }
                }

            InputsIntent.ClearSubmitted -> updateState { it.copy(submitted = emptyList()) }
        }
    }
}
