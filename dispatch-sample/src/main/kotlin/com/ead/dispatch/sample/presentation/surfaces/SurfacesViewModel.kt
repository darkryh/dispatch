package com.ead.dispatch.sample.presentation.surfaces

import com.ead.dispatch.modifier.BorderStyle
import com.ead.dispatch.sample.designsystem.PlaygroundIntent
import com.ead.dispatch.sample.designsystem.wrapIndex
import com.ead.dispatch.viewmodel.MviViewModel
import com.ead.dispatch.widget.DividerStyle
import com.ead.dispatch.widget.TextOverflow

/** Labels for the surface-style cycle; the preview maps these to `SurfaceStyle.None/lines/fill`. */
val SURFACE_OPTIONS = listOf("None", "Lines", "Fill")

/**
 * State of the Surfaces & Dividers playground. [selected] is the highlighted control row; the rest
 * are the live indices/flags fed into the preview widgets.
 */
data class SurfacesState(
    val selected: Int = 0,
    val borderStyle: Int = 0,
    val dividerStyle: Int = 0,
    val surfaceStyle: Int = 0,
    val overflow: Int = 0,
    val markdown: Boolean = false,
) {
    val borderStyleValue: BorderStyle get() = BorderStyle.entries[borderStyle]
    val dividerStyleValue: DividerStyle get() = DividerStyle.entries[dividerStyle]
    val overflowValue: TextOverflow get() = TextOverflow.entries[overflow]
}

/** Number of editable control rows; selection wraps within this count. */
const val SURFACES_CONTROL_COUNT = 5

/**
 * Drives the Surfaces & Dividers playground. Demonstrates [MviViewModel] with the shared
 * [PlaygroundIntent]: ↑/↓ moves [SurfacesState.selected], ←/→ cycles the selected border/divider/
 * surface/overflow style, and Space flips the Markdown rendering toggle.
 */
class SurfacesViewModel : MviViewModel<SurfacesState, PlaygroundIntent>(SurfacesState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, SURFACES_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(borderStyle = wrapIndex(state.borderStyle, intent.delta, BorderStyle.entries.size))
                        1 -> state.copy(dividerStyle = wrapIndex(state.dividerStyle, intent.delta, DividerStyle.entries.size))
                        2 -> state.copy(surfaceStyle = wrapIndex(state.surfaceStyle, intent.delta, SURFACE_OPTIONS.size))
                        3 -> state.copy(overflow = wrapIndex(state.overflow, intent.delta, TextOverflow.entries.size))
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle ->
                updateState { state ->
                    when (state.selected) {
                        4 -> state.copy(markdown = !state.markdown)
                        else -> state
                    }
                }
        }
    }
}
