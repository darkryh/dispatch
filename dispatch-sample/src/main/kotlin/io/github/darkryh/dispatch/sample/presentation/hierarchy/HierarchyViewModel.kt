package io.github.darkryh.dispatch.sample.presentation.hierarchy

import io.github.darkryh.dispatch.viewmodel.MviViewModel

/**
 * State of the Hierarchy & Command playground. All three fields are read-only echoes surfaced in the
 * controls pane; the interactive widgets in the preview own their own keyboard state.
 *
 * @param lastDecision the label (or custom text) submitted from the [io.github.darkryh.dispatch.widget.DecisionPrompt].
 * @param paletteInput the live input feeding the [io.github.darkryh.dispatch.widget.CommandPalette].
 * @param lastCommand the label of the last command run from the palette.
 */
data class HierarchyState(
    val lastDecision: String = "—",
    val paletteInput: String = "",
    val lastCommand: String = "—",
)

/**
 * Intents for the Hierarchy & Command playground.
 *
 * [SubmitDecision] records a decision selection, [UpdatePalette] mirrors the palette input, and
 * [RunCommand] records the command chosen from the palette.
 */
sealed interface HierarchyIntent {
    data class SubmitDecision(
        val text: String,
    ) : HierarchyIntent

    data class UpdatePalette(
        val text: String,
    ) : HierarchyIntent

    data class RunCommand(
        val label: String,
    ) : HierarchyIntent
}

/**
 * Drives the Hierarchy & Command playground. Unlike the other playgrounds this screen does **not**
 * use the shared [io.github.darkryh.dispatch.sample.designsystem.PlaygroundController]: the preview widgets
 * (Tree, DecisionPrompt, CommandPalette) each own the arrow keys while focused, so the view-model
 * only records the values they emit through these custom [HierarchyIntent]s.
 */
class HierarchyViewModel : MviViewModel<HierarchyState, HierarchyIntent>(HierarchyState()) {
    override suspend fun handleIntent(intent: HierarchyIntent) {
        when (intent) {
            is HierarchyIntent.SubmitDecision -> updateState { it.copy(lastDecision = intent.text) }
            is HierarchyIntent.UpdatePalette -> updateState { it.copy(paletteInput = intent.text) }
            is HierarchyIntent.RunCommand -> updateState { it.copy(lastCommand = intent.label) }
        }
    }
}
