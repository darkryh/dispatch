package io.github.darkryh.dispatch.sample.presentation.buttons

import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel
import io.github.darkryh.dispatch.widget.ButtonStyle
import io.github.darkryh.dispatch.widget.ToggleStyle

/** Selectable values for the radio-group and segmented-button demos. */
val RADIO_OPTIONS = listOf("Compact", "Cozy", "Comfortable")
val SEGMENT_OPTIONS = listOf("Left", "Center", "Right")

/**
 * State of the Buttons & Selection playground. [selected] is the highlighted control row; the rest
 * are the live properties fed into the preview widgets.
 */
data class ButtonsState(
    val selected: Int = 0,
    val buttonStyle: Int = 0,
    val toggleStyle: Int = 0,
    val toggleOn: Boolean = true,
    val radio: Int = 0,
    val segment: Int = 1,
    val enabled: Boolean = true,
    val clicks: Int = 0,
) {
    val buttonStyleValue: ButtonStyle get() = ButtonStyle.entries[buttonStyle]
    val toggleStyleValue: ToggleStyle get() = ToggleStyle.entries[toggleStyle]
}

/** Number of editable control rows; selection wraps within this count. */
const val BUTTONS_CONTROL_COUNT = 6

/**
 * Drives the Buttons & Selection playground. Demonstrates [MviViewModel] with the shared
 * [PlaygroundIntent]: ↑/↓ moves [ButtonsState.selected], ←/→ cycles the selected style/option, and
 * Space flips the selected boolean. Real button clicks arrive through [onButtonClick].
 */
class ButtonsViewModel : MviViewModel<ButtonsState, PlaygroundIntent>(ButtonsState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, BUTTONS_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(buttonStyle = wrapIndex(state.buttonStyle, intent.delta, ButtonStyle.entries.size))
                        1 -> state.copy(toggleStyle = wrapIndex(state.toggleStyle, intent.delta, ToggleStyle.entries.size))
                        3 -> state.copy(radio = wrapIndex(state.radio, intent.delta, RADIO_OPTIONS.size))
                        4 -> state.copy(segment = wrapIndex(state.segment, intent.delta, SEGMENT_OPTIONS.size))
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle ->
                updateState { state ->
                    when (state.selected) {
                        2 -> state.copy(toggleOn = !state.toggleOn)
                        5 -> state.copy(enabled = !state.enabled)
                        else -> state
                    }
                }
        }
    }

    /** Called by the focused preview [io.github.darkryh.dispatch.widget.Button] when activated with Enter. */
    fun onButtonClick() = updateState { it.copy(clicks = it.clicks + 1) }
}
