package io.github.darkryh.dispatch.sample.presentation.progress

import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel
import io.github.darkryh.dispatch.widget.LinearProgressIndicatorStyle
import io.github.darkryh.dispatch.widget.SpinnerStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Speed presets cycled by the "Speed" control; index maps into this list. */
val PROGRESS_SPEEDS = listOf("Slow", "Normal", "Fast")

/**
 * State of the Progress playground. [selected] is the highlighted control row; the rest are the live
 * properties fed into the preview. [frame] and [progress] are advanced by the view-model's animation
 * loop (see [ProgressViewModel]) rather than by user input — they make the spinners and bars move.
 */
data class ProgressState(
    val selected: Int = 0,
    val barStyle: Int = 0,
    val spinnerStyle: Int = 0,
    val showPercent: Boolean = true,
    val playing: Boolean = true,
    val speed: Int = 1,
    val frame: Int = 0,
    val progress: Float = 0f,
) {
    val barStyleValue: LinearProgressIndicatorStyle get() = LinearProgressIndicatorStyle.entries[barStyle]
    val spinnerStyleValue: SpinnerStyle get() = SpinnerStyle.entries[spinnerStyle]
}

/** Number of editable control rows; selection wraps within this count. */
const val PROGRESS_CONTROL_COUNT = 5

/**
 * Drives the Progress playground. Unlike the other category screens, this view-model **owns an
 * animation loop**: an [init]-launched coroutine on [viewModelScope] ticks forever, and on each tick
 * (when [ProgressState.playing] is true) advances [ProgressState.frame] and wraps
 * [ProgressState.progress], so the preview spinners and bars animate without any timer in the UI
 * layer. The tick interval comes from [ProgressState.speed] via [speedToMillis].
 *
 * User input still flows through the shared [PlaygroundIntent]: ↑/↓ moves [ProgressState.selected],
 * ←/→ cycles the selected style/speed, and Space flips the selected boolean.
 */
class ProgressViewModel : MviViewModel<ProgressState, PlaygroundIntent>(ProgressState()) {
    init {
        viewModelScope.launch {
            while (isActive) {
                delay(speedToMillis(currentState.speed))
                if (currentState.playing) {
                    updateState { it.copy(frame = it.frame + 1, progress = (it.progress + 0.02f) % 1f) }
                }
            }
        }
    }

    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, PROGRESS_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(barStyle = wrapIndex(state.barStyle, intent.delta, LinearProgressIndicatorStyle.entries.size))
                        1 -> state.copy(spinnerStyle = wrapIndex(state.spinnerStyle, intent.delta, SpinnerStyle.entries.size))
                        4 -> state.copy(speed = wrapIndex(state.speed, intent.delta, PROGRESS_SPEEDS.size))
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle ->
                updateState { state ->
                    when (state.selected) {
                        2 -> state.copy(showPercent = !state.showPercent)
                        3 -> state.copy(playing = !state.playing)
                        else -> state
                    }
                }
        }
    }

    /** Tick interval in milliseconds for each [ProgressState.speed] preset (Slow/Normal/Fast). */
    private fun speedToMillis(speed: Int): Long =
        when (speed) {
            0 -> 160L
            2 -> 40L
            else -> 90L
        }
}
