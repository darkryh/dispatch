@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.progress

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.ControlPanel
import io.github.darkryh.dispatch.sample.designsystem.ControlSpec
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundController
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundScaffold
import io.github.darkryh.dispatch.sample.widgets.TransferProgress
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.LinearProgressIndicator
import io.github.darkryh.dispatch.widget.LinearProgressIndicatorStyle
import io.github.darkryh.dispatch.widget.LoadingIndicator
import io.github.darkryh.dispatch.widget.Spinner
import io.github.darkryh.dispatch.widget.SpinnerStyle
import io.github.darkryh.dispatch.widget.Text

/**
 * Progress playground.
 *
 * Demonstrates the indeterminate and determinate progress widgets — [LinearProgressIndicator] (all
 * six [LinearProgressIndicatorStyle]s), [Spinner] (all six [SpinnerStyle]s), [LoadingIndicator], and
 * the sample [TransferProgress] — all animating live. The motion is driven by the view-model's own
 * coroutine loop (see [ProgressViewModel]), so the controls only tune *how* it animates: bar style,
 * spinner style, the percentage readout, play/pause, and the tick speed.
 */
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Progress",
        subtitle = "Bars, spinners and loaders — animated by a view-model loop. Space toggles, ←/→ cycles.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Bar style", LinearProgressIndicatorStyle.entries.map { it.name }, state.barStyle),
                        ControlSpec.Cycle("Spinner style", SpinnerStyle.entries.map { it.name }, state.spinnerStyle),
                        ControlSpec.Toggle("Show %", state.showPercent),
                        ControlSpec.Toggle("Playing", state.playing),
                        ControlSpec.Cycle("Speed", PROGRESS_SPEEDS, state.speed),
                    ),
                selected = state.selected,
            )
        },
        preview = { ProgressPreview(state) },
    )
}

private const val TRANSFER_TOTAL_BYTES = 10_000_000L

@Composable
private fun ProgressPreview(state: ProgressState) {
    val theme = LocalTheme.current
    Column {
        Text("LinearProgressIndicator — ${state.barStyleValue.name}", style = theme.muted)
        LinearProgressIndicator(
            progress = state.progress,
            style = state.barStyleValue,
            showPercentage = state.showPercent,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(1))

        Text("All bar styles (at 60%)", style = theme.muted)
        LinearProgressIndicatorStyle.entries.forEach { style ->
            Row {
                Text(style.name.padEnd(8), style = theme.muted)
                Text(" ", style = theme.muted)
                LinearProgressIndicator(progress = 0.6f, style = style, showPercentage = true)
            }
        }
        Spacer(Modifier.height(1))

        Text("Spinner — ${state.spinnerStyleValue.name}", style = theme.muted)
        Row {
            Spinner(frame = state.frame, style = state.spinnerStyleValue, textStyle = theme.accent)
            Spacer(Modifier.width(2))
            LoadingIndicator(frame = state.frame, text = "Working…", style = state.spinnerStyleValue)
        }
        Spacer(Modifier.height(1))

        Text("TransferProgress", style = theme.muted)
        TransferProgress(
            progress = state.progress,
            bytesTransferred = (state.progress * TRANSFER_TOTAL_BYTES).toLong(),
            totalBytes = TRANSFER_TOTAL_BYTES,
            style = state.barStyleValue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
