@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import com.github.ajalt.mordant.rendering.TextStyles
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.runtime.LocalHibernation
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.widget.DividerStyle
import io.github.darkryh.dispatch.widget.HorizontalDivider
import io.github.darkryh.dispatch.widget.Text

/**
 * Whether the hibernation status overlay is shown. On by default so idle hibernation is visible in a
 * normal sample run; set `DISPATCH_SAMPLE_DEBUG_OVERLAY` to `0`/`false`/`off`/`no` to hide it.
 */
internal fun isHibernationOverlayEnabled(): Boolean =
    when (System.getenv("DISPATCH_SAMPLE_DEBUG_OVERLAY")?.trim()?.lowercase()) {
        "0", "false", "off", "no" -> false
        else -> true
    }

/**
 * A bottom-of-screen status banner that appears **only while the runtime is hibernating**.
 *
 * While awake it renders nothing, so the screen keeps its full height and there is no permanent footer
 * noise. The moment idle hibernation kicks in, [LocalHibernation]'s snapshot-backed `isHibernating`
 * flips and a divider + a single plain-language line appear: that the app is resting and how to leave it.
 *
 * Deliberately **static** — no pulse timer, no live heap readout. Hibernation is meant to be a quiet,
 * near-zero-allocation state; an animated banner would force a repaint on every tick and generate the
 * very heap churn it is supposed to avoid. So the banner paints once on entry and then stays silent
 * until a keypress wakes the app. Renders nothing when no `HibernationHandle` is installed.
 */
@Composable
fun HibernationOverlay(modifier: Modifier = Modifier) {
    val handle = LocalHibernation.current ?: return
    val theme = LocalTheme.current

    // Only surface the banner while hibernating — nothing is drawn while the app is awake.
    if (!handle.isHibernating) return

    // Kept short on purpose: the row clips at the terminal width, so the message must fit narrow panes.
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(style = DividerStyle.Light, modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("●", style = theme.warning)
            Spacer(Modifier.width(1))
            Text("Hibernating", style = theme.warning + TextStyles.bold)
            Spacer(Modifier.width(2))
            Text("·", style = theme.muted + TextStyles.dim)
            Spacer(Modifier.width(2))
            Text("press any key to wake", style = theme.success)
        }
    }
}
