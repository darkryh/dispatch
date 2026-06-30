package io.github.darkryh.dispatch.sample.presentation.review

import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel
import io.github.darkryh.dispatch.widget.DiffFocus

/** Selectable context-window sizes for the diff preview; the value feeds [FileDiff.contextLines]. */
val CONTEXT_OPTIONS = listOf(1, 2, 3, 5)

/** Human-readable labels for [CONTEXT_OPTIONS], shown in the cycle control. */
val CONTEXT_LABELS: List<String> = CONTEXT_OPTIONS.map { it.toString() }

/**
 * State of the Diff & Review playground. [selected] is the highlighted control row; the rest are the
 * live properties fed into the [io.github.darkryh.dispatch.widget.FileDiff] preview.
 *
 * @param focus index into [DiffFocus.entries] selecting which hunks are emphasised.
 * @param contextLines unchanged lines of context kept around each focused hunk (one of [CONTEXT_OPTIONS]).
 * @param showStats whether the summary stats line is rendered above the diff body.
 */
data class ReviewState(
    val selected: Int = 0,
    val focus: Int = 0,
    val contextLines: Int = 3,
    val showStats: Boolean = true,
) {
    /** The currently selected diff focus mode. */
    val focusValue: DiffFocus get() = DiffFocus.entries[focus]

    /** The context-line count passed straight to the widget. */
    val contextLinesValue: Int get() = contextLines

    /** Index of [contextLines] within [CONTEXT_OPTIONS], used to drive the cycle control. */
    val contextIndex: Int get() = CONTEXT_OPTIONS.indexOf(contextLines).coerceAtLeast(0)
}

/** Number of editable control rows; selection wraps within this count. */
const val REVIEW_CONTROL_COUNT = 3

/**
 * Drives the Diff & Review playground. Demonstrates [MviViewModel] with the shared
 * [PlaygroundIntent]: ↑/↓ moves [ReviewState.selected], ←/→ cycles the selected diff focus or
 * context window, and Space flips the stats line on the highlighted row.
 */
class ReviewViewModel : MviViewModel<ReviewState, PlaygroundIntent>(ReviewState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, REVIEW_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(focus = wrapIndex(state.focus, intent.delta, DiffFocus.entries.size))
                        1 -> {
                            val next = wrapIndex(state.contextIndex, intent.delta, CONTEXT_OPTIONS.size)
                            state.copy(contextLines = CONTEXT_OPTIONS[next])
                        }
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle ->
                updateState { state ->
                    when (state.selected) {
                        2 -> state.copy(showStats = !state.showStats)
                        else -> state
                    }
                }
        }
    }
}
