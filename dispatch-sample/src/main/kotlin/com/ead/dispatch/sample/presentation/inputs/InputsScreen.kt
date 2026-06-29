@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.inputs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.designsystem.ControlPanel
import com.ead.dispatch.sample.designsystem.ControlSpec
import com.ead.dispatch.sample.designsystem.PlaygroundScaffold
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.BasicTextFieldRenderer
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.PasswordField
import com.ead.dispatch.widget.Surface
import com.ead.dispatch.widget.SurfaceStyle
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextField
import com.ead.dispatch.widget.rememberInputHistoryIndexState
import com.ead.dispatch.widget.rememberTextFieldState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

/**
 * Inputs playground.
 *
 * Demonstrates: the value-based [TextField] (with input history fed from submitted names and a
 * leading icon), the [TextFieldState]-based [TextField] overload, [PasswordField] character masking,
 * and the display-only [BasicTextFieldRenderer]. The fields own the keyboard themselves — they
 * register at priority -1 and handle typing, cursor movement, Tab focus traversal and ↑/↓ history —
 * so this screen deliberately skips [com.ead.dispatch.sample.designsystem.PlaygroundController]; the
 * controls pane is a read-only echo of the live state.
 */
@Composable
fun InputsScreen(viewModel: InputsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundScaffold(
        title = "Inputs",
        subtitle = "Text fields, password masking and ↑/↓ history. Tab moves between fields.",
        showControlHints = false,
        hints =
            listOf(
                KeyHint("Tab", "next field"),
                KeyHint("↑/↓", "history"),
                KeyHint("Enter", "submit"),
            ),
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Value("Submitted", state.submitted.size.toString()),
                        ControlSpec.Value("Last", state.submitted.lastOrNull() ?: "—"),
                        ControlSpec.Value("Notes len", state.notes.length.toString()),
                    ),
                selected = -1,
            )
        },
        preview = { InputsPreview(state, viewModel::sendIntent) },
    )
}

@Composable
private fun InputsPreview(
    state: InputsState,
    onIntent: (InputsIntent) -> Unit,
) {
    val theme = LocalTheme.current
    val notesState = rememberTextFieldState()
    val nameHistory = rememberInputHistoryIndexState()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Value-based TextField — Enter submits the trimmed name; ↑/↓ recall earlier submissions.
        Text("TextField (value) — Enter submits", style = theme.muted)
        Surface(style = SurfaceStyle.fill(rgb("#303846"))) {
            TextField(
                value = state.name,
                onValueChange = { onIntent(InputsIntent.UpdateName(it)) },
                modifier = Modifier.fillMaxWidth(),
                icon = "name ",
                placeholder = "Ada Lovelace",
                onSubmit = { onIntent(InputsIntent.Submit) },
                historyItems = state.submitted,
                historyIndexState = nameHistory,
            )
        }
        Spacer(Modifier.height(1))

        // PasswordField — every character is masked with the mask char.
        Text("PasswordField — masked input", style = theme.muted)
        PasswordField(
            value = state.password,
            onValueChange = { onIntent(InputsIntent.UpdatePassword(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = "secret",
            maskChar = '●',
        )
        Spacer(Modifier.height(1))

        // State-based TextField — drives a TextFieldState and notifies the VM of edits.
        Text("TextField (TextFieldState) — multi-line notes", style = theme.muted)
        TextField(
            state = notesState,
            modifier = Modifier.fillMaxWidth(),
            icon = "› ",
            placeholder = "Jot a few notes…",
            maxLines = 3,
            onSubmit = { onIntent(InputsIntent.UpdateNotes(notesState.value)) },
        )
        Spacer(Modifier.height(1))

        // BasicTextFieldRenderer — pure display widget, no keyboard handling.
        Text("BasicTextFieldRenderer — display only", style = theme.muted)
        BasicTextFieldRenderer(
            value = state.name.ifEmpty { "—" },
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            icon = "echo ",
            enabled = false,
            showCursor = false,
        )
        Spacer(Modifier.height(1))

        Text("Tab moves between fields · ↑/↓ recalls history · Enter submits", style = theme.muted)
    }
}
