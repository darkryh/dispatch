@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.buttons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.designsystem.ControlPanel
import com.ead.dispatch.sample.designsystem.ControlSpec
import com.ead.dispatch.sample.designsystem.PlaygroundController
import com.ead.dispatch.sample.designsystem.PlaygroundIntent
import com.ead.dispatch.sample.designsystem.PlaygroundScaffold
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.Button
import com.ead.dispatch.widget.ButtonRow
import com.ead.dispatch.widget.ButtonStyle
import com.ead.dispatch.widget.IconButton
import com.ead.dispatch.widget.RadioButton
import com.ead.dispatch.widget.SegmentedButton
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.ToggleButton
import com.ead.dispatch.widget.ToggleStyle

/**
 * Buttons & Selection playground.
 *
 * Demonstrates: [Button] (all five [ButtonStyle]s), [IconButton], [ButtonRow], [ToggleButton] (all
 * five [ToggleStyle]s), [RadioButton] groups, [SegmentedButton], and `enabled` state — each driven
 * live from the controls pane. The primary [Button] is focusable, so Tab focuses it and Enter
 * increments the click counter.
 */
@Composable
fun ButtonsScreen(viewModel: ButtonsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Buttons & Selection",
        subtitle = "Cycle styles with ←/→, flip booleans with Space, Tab+Enter to click.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Button style", ButtonStyle.entries.map { it.name }, state.buttonStyle),
                        ControlSpec.Cycle("Toggle style", ToggleStyle.entries.map { it.name }, state.toggleStyle),
                        ControlSpec.Toggle("Toggle value", state.toggleOn),
                        ControlSpec.Cycle("Radio", RADIO_OPTIONS, state.radio),
                        ControlSpec.Cycle("Segment", SEGMENT_OPTIONS, state.segment),
                        ControlSpec.Toggle("Enabled", state.enabled),
                        ControlSpec.Value("Clicks", state.clicks.toString()),
                    ),
                selected = state.selected,
            )
        },
        preview = { ButtonsPreview(state, viewModel::onButtonClick) },
    )
}

@Composable
private fun ButtonsPreview(
    state: ButtonsState,
    onClick: () -> Unit,
) {
    val theme = LocalTheme.current
    Column {
        Text("Button — ${state.buttonStyleValue.name}", style = theme.muted)
        Row {
            Button(
                text = "Activate",
                onClick = onClick,
                enabled = state.enabled,
                style = state.buttonStyleValue,
            )
            Spacer(Modifier.width(2))
            IconButton(icon = "★", onClick = onClick, enabled = state.enabled)
        }
        Spacer(Modifier.height(1))

        Text("ButtonRow", style = theme.muted)
        ButtonRow {
            ButtonStyle.entries.forEach { style ->
                Button(text = style.name, onClick = onClick, enabled = state.enabled, style = style)
            }
        }
        Spacer(Modifier.height(1))

        Text("ToggleButton — ${state.toggleStyleValue.name}", style = theme.muted)
        ToggleButton(
            checked = state.toggleOn,
            onCheckedChange = {},
            label = if (state.toggleOn) "Enabled" else "Disabled",
            enabled = state.enabled,
            style = state.toggleStyleValue,
        )
        Spacer(Modifier.height(1))

        Text("RadioButton group", style = theme.muted)
        RADIO_OPTIONS.forEachIndexed { index, option ->
            RadioButton(
                selected = index == state.radio,
                onClick = {},
                label = option,
                enabled = state.enabled,
            )
        }
        Spacer(Modifier.height(1))

        Text("SegmentedButton", style = theme.muted)
        SegmentedButton(
            value = SEGMENT_OPTIONS[state.segment],
            options = SEGMENT_OPTIONS,
            onValueChange = {},
            enabled = state.enabled,
        )
    }
}
