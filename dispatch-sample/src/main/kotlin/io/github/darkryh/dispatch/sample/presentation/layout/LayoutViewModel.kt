package io.github.darkryh.dispatch.sample.presentation.layout

import io.github.darkryh.dispatch.layout.Alignment
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel

/**
 * The nine 2-D [Alignment.Alignment2D] values, paired with a short label, in reading order. Drives
 * the live [io.github.darkryh.dispatch.layout.Box] `contentAlignment` demo.
 */
val ALIGNMENTS: List<Pair<String, Alignment.Alignment2D>> =
    listOf(
        "TopStart" to Alignment.TopStart,
        "TopCenter" to Alignment.TopCenter,
        "TopEnd" to Alignment.TopEnd,
        "CenterStart" to Alignment.CenterStart,
        "Center" to Alignment.Center,
        "CenterEnd" to Alignment.CenterEnd,
        "BottomStart" to Alignment.BottomStart,
        "BottomCenter" to Alignment.BottomCenter,
        "BottomEnd" to Alignment.BottomEnd,
    )

/**
 * The six fixed-distribution [Arrangement.Horizontal] values, paired with a label. Drives the live
 * [io.github.darkryh.dispatch.layout.Row] `horizontalArrangement` demo.
 */
val ARRANGEMENTS: List<Pair<String, Arrangement.Horizontal>> =
    listOf(
        "Start" to Arrangement.Start,
        "Center" to Arrangement.Center,
        "End" to Arrangement.End,
        "SpaceBetween" to Arrangement.SpaceBetween,
        "SpaceEvenly" to Arrangement.SpaceEvenly,
        "SpaceAround" to Arrangement.SpaceAround,
    )

/** Padding amounts offered by the "Padding" control; the option index equals the padding value. */
val PADDING_OPTIONS = listOf("0", "1", "2", "3")

/** `maxItemsInEachRow` values offered by the "Flow max items" control. */
val FLOW_OPTIONS = listOf(2, 3, 4, 6)

/** Number of editable control rows; selection wraps within this count. */
const val LAYOUT_CONTROL_COUNT = 4

/**
 * State of the Layout playground. [selected] is the highlighted control row; the rest are the live
 * properties fed into the preview. [alignment] / [arrangement] index into [ALIGNMENTS] / [ARRANGEMENTS],
 * [padding] is both the option index and the cell count, and [flowMax] is the chosen `maxItemsInEachRow`.
 */
data class LayoutState(
    val selected: Int = 0,
    val alignment: Int = 0,
    val arrangement: Int = 0,
    val padding: Int = 1,
    val flowMax: Int = 4,
)

/**
 * Drives the Layout playground. Demonstrates [MviViewModel] with the shared [PlaygroundIntent]:
 * ↑/↓ moves [LayoutState.selected] and ←/→ cycles the selected option (Box alignment, Row
 * arrangement, padding, or flow item cap). Space is unused here.
 */
class LayoutViewModel : MviViewModel<LayoutState, PlaygroundIntent>(LayoutState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, LAYOUT_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(alignment = wrapIndex(state.alignment, intent.delta, ALIGNMENTS.size))
                        1 -> state.copy(arrangement = wrapIndex(state.arrangement, intent.delta, ARRANGEMENTS.size))
                        2 -> state.copy(padding = wrapIndex(state.padding, intent.delta, PADDING_OPTIONS.size))
                        3 -> {
                            val current = FLOW_OPTIONS.indexOf(state.flowMax).coerceAtLeast(0)
                            state.copy(flowMax = FLOW_OPTIONS[wrapIndex(current, intent.delta, FLOW_OPTIONS.size)])
                        }
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle -> Unit
        }
    }
}
