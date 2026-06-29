package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay

/** Braille spinner frames — identical to the library's `Spinner` (dispatch-widgets ProgressIndicators). */
val SpinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")

fun spinnerGlyph(frame: Int): String = SpinnerFrames[((frame % SpinnerFrames.size) + SpinnerFrames.size) % SpinnerFrames.size]

/**
 * Reveal [full] one glyph at a time and return the visible prefix. Under reduced motion (or when
 * [start] is false) the whole string is returned immediately.
 */
@Composable
fun rememberTypewriter(
    full: String,
    stepMs: Int = Tempo.CHAR_TYPE,
    start: Boolean = true,
): String {
    val reduced = LocalReducedMotion.current
    var shown by remember(full) { mutableStateOf(if (reduced || !start) full.length else 0) }
    LaunchedEffect(full, start, reduced) {
        if (reduced || !start) {
            shown = full.length
            return@LaunchedEffect
        }
        shown = 0
        while (shown < full.length) {
            delay(stepMs.toLong())
            shown++
        }
    }
    return full.substring(0, shown.coerceIn(0, full.length))
}

/**
 * A hard square-wave blink toggle at [Tempo.CURSOR_BLINK]. Returns a boolean rather than animating
 * alpha continuously, so it only flips state ~twice a second instead of recomposing every frame.
 * Solid (always true) under reduced motion.
 */
@Composable
fun rememberCursorOn(resetKey: Any? = null): Boolean {
    val reduced = LocalReducedMotion.current
    if (reduced) return true
    var on by remember { mutableStateOf(true) }
    LaunchedEffect(resetKey) { on = true }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Tempo.CURSOR_BLINK.toLong())
            on = !on
        }
    }
    return on
}

/** A frame counter that advances only while [running]; drives the build-log spinner. */
@Composable
fun rememberSpinnerFrame(running: Boolean): Int {
    var frame by remember { mutableStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(Tempo.SPINNER_FRAME.toLong())
            frame++
        }
    }
    return frame
}

/**
 * A faint static scanline texture drawn behind content (≈3% white lines every 3px). Drawn in the
 * draw phase so it never triggers recomposition; disabled entirely under reduced motion.
 */
fun Modifier.scanlines(enabled: Boolean): Modifier =
    if (!enabled) {
        this
    } else {
        drawBehind {
            val gap = 3f
            val line = Color.White.copy(alpha = 0.03f)
            var y = 0f
            while (y < size.height) {
                drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += gap
            }
        }
    }

/** A slow, subtle accent-brightness wobble for the wordmark. Static (1f) under reduced motion. */
@Composable
fun rememberAccentFlicker(): Float {
    if (LocalReducedMotion.current) return 1f
    val transition = rememberInfiniteTransition(label = "flicker")
    val value by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(2400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "flickerAlpha",
    )
    return value
}
