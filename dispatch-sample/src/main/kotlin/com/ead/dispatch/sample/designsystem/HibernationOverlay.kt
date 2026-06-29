@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LocalHibernation
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyles
import kotlinx.coroutines.delay

/**
 * Whether the debug hibernation overlay should be shown. Gated on the `DISPATCH_SAMPLE_DEBUG_OVERLAY`
 * environment variable so it never appears in a normal run.
 */
internal fun isHibernationOverlayEnabled(): Boolean =
    System.getenv("DISPATCH_SAMPLE_DEBUG_OVERLAY")?.isNotBlank() == true

/**
 * A single dim status line that surfaces the runtime's idle-hibernation state for live demos.
 *
 * Reads [LocalHibernation] and shows: AWAKE/HIBERNATING, active-vs-idle paint FPS, the idle countdown
 * in seconds, and the current JVM heap usage in MB. A `LaunchedEffect` bumps a tick every ~500ms to
 * drive recomposition, because the countdown and heap are polled values that don't auto-recompose.
 * A small heartbeat dot flips with each tick so the cadence is visible (and freezes at idle FPS once
 * hibernating). When no [HibernationHandle] is installed (hibernation disabled) it renders nothing.
 */
@Composable
fun HibernationOverlay(modifier: Modifier = Modifier) {
    val handle = LocalHibernation.current ?: return
    val theme = LocalTheme.current

    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

    // Read the polled values on every tick so the time-based readouts stay live.
    val hibernating = handle.isHibernating
    val countdownSeconds = (handle.idleCountdownMillis() + 999L) / 1000L
    val heapUsedMb = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / 1024 / 1024 }
    val currentFps = if (hibernating) handle.idleFps else handle.activeFps

    val stateLabel = if (hibernating) "HIBERNATING" else "AWAKE"
    val stateStyle = if (hibernating) theme.warning else theme.success
    val heartbeat = if (tick % 2 == 0) "•" else " "

    Row(modifier = modifier.fillMaxWidth()) {
        Text("hib", style = theme.muted + TextStyles.dim)
        Spacer(Modifier.width(1))
        Text(heartbeat, style = stateStyle)
        Spacer(Modifier.width(1))
        Text(stateLabel, style = stateStyle)
        Spacer(Modifier.width(2))
        Text("fps ${currentFps}↻${handle.activeFps}/${handle.idleFps}", style = theme.muted)
        Spacer(Modifier.width(2))
        Text(if (hibernating) "idle —" else "idle ${countdownSeconds}s", style = theme.muted)
        Spacer(Modifier.width(2))
        Text("heap ${heapUsedMb}MB", style = theme.muted)
    }
}
