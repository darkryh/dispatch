@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.ControlPanel
import io.github.darkryh.dispatch.sample.designsystem.ControlSpec
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundController
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundScaffold
import io.github.darkryh.dispatch.sample.widgets.DiffReviewAction
import io.github.darkryh.dispatch.sample.widgets.DiffReviewPanel
import io.github.darkryh.dispatch.sample.widgets.DiffReviewPanelState
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.DiffFocus
import io.github.darkryh.dispatch.widget.FileDiff
import io.github.darkryh.dispatch.widget.KeyHint
import io.github.darkryh.dispatch.widget.Text

/** Original source shown on the "before" side of the demo diff. */
private val BEFORE =
    """
    fun greet(name: String): String {
        val prefix = "Hello"
        return prefix + ", " + name
    }
    """.trimIndent()

/** Edited source shown on the "after" side — adds a parameter, changes lines, inserts one. */
private val AFTER =
    """
    fun greet(name: String, excited: Boolean = false): String {
        val prefix = "Hi"
        val suffix = if (excited) "!" else "."
        return "${'$'}prefix, ${'$'}name${'$'}suffix"
    }
    """.trimIndent()

/**
 * Diff & Review playground.
 *
 * Demonstrates: [FileDiff] (live [DiffFocus] modes, adjustable `contextLines`, optional stats line)
 * and the sample [DiffReviewPanel] composite (paged diff body plus Approve/Reject review actions).
 * Cycle focus and context with ←/→ and flip the stats line with Space.
 */
@Composable
fun ReviewScreen(viewModel: ReviewViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Diff & Review",
        subtitle = "Cycle focus & context with ←/→, toggle stats with Space.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Focus", DiffFocus.entries.map { it.name }, state.focus),
                        ControlSpec.Cycle("Context", CONTEXT_LABELS, state.contextIndex),
                        ControlSpec.Toggle("Show stats", state.showStats),
                    ),
                selected = state.selected,
            )
        },
        preview = { ReviewPreview(state) },
    )
}

@Composable
private fun ReviewPreview(state: ReviewState) {
    val theme = LocalTheme.current
    Column {
        Text("FileDiff — focus ${state.focusValue.name}, context ${state.contextLinesValue}", style = theme.muted)
        FileDiff(
            before = BEFORE,
            after = AFTER,
            path = "Example.kt",
            focus = state.focusValue,
            contextLines = state.contextLinesValue,
            showStats = state.showStats,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(1))
        Text("DiffReviewPanel — Approve / Reject", style = theme.muted)
        DiffReviewPanel(
            state =
                DiffReviewPanelState(
                    title = "Review change",
                    subtitle = "greet() signature update",
                    statusLine = "1 file changed",
                    before = BEFORE,
                    after = AFTER,
                    path = "Example.kt",
                    focus = state.focusValue,
                    contextLines = state.contextLinesValue,
                    pageSizeRows = 12,
                    actions =
                        listOf(
                            DiffReviewAction("Approve"),
                            DiffReviewAction("Reject"),
                        ),
                    selectedActionIndex = 0,
                    actionsFocused = true,
                ),
            maxVisibleRows = 8,
            compact = false,
            actionsKeyHints =
                listOf(
                    KeyHint("←/→", "select"),
                    KeyHint("Enter", "confirm"),
                ),
        )
    }
}
