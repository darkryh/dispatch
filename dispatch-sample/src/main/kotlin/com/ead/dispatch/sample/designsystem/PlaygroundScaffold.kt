@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Box
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.widget.KeyHint

/**
 * The shared layout every category screen builds on: a live **Preview** pane beside an editable
 * **Controls** pane, wrapped in an [AppScaffold]. On narrow terminals the two panes stack
 * vertically instead of sitting side by side.
 *
 * This composable itself demonstrates [Row], [Box], [Modifier.weight] and [Pane]. The uniform
 * control keys (↑↓ / ←→ / Space) are appended to the hint bar automatically — set
 * [showControlHints] to false on screens whose preview widgets own the keyboard themselves.
 *
 * @param preview the widget(s) under test, re-rendered live as the controls change.
 * @param controls the editable [ControlPanel] describing what can be tweaked.
 */
@Composable
fun PlaygroundScaffold(
    title: String,
    subtitle: String,
    hints: List<KeyHint> = emptyList(),
    showControlHints: Boolean = true,
    controls: @Composable () -> Unit,
    preview: @Composable () -> Unit,
) {
    val width = LocalTerminalWidth.current
    val stacked = width < SIDE_BY_SIDE_MIN_WIDTH

    AppScaffold(
        title = title,
        subtitle = subtitle,
        hints = (if (showControlHints) playgroundHints else emptyList()) + hints,
    ) {
        if (stacked) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Pane(title = "Preview") { preview() }
                Spacer(Modifier.height(1))
                Pane(title = "Controls") { controls() }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(PREVIEW_WEIGHT)) {
                    Pane(title = "Preview") { preview() }
                }
                Spacer(Modifier.fillMaxWidth(GAP_FRACTION))
                Box(modifier = Modifier.weight(CONTROLS_WEIGHT)) {
                    Pane(title = "Controls") { controls() }
                }
            }
        }
    }
}

private val playgroundHints =
    listOf(
        KeyHint("↑/↓", "control"),
        KeyHint("←/→", "change"),
        KeyHint("Space", "toggle"),
    )

private const val SIDE_BY_SIDE_MIN_WIDTH = 90
private const val PREVIEW_WEIGHT = 0.62f
private const val CONTROLS_WEIGHT = 0.38f
private const val GAP_FRACTION = 0.02f
