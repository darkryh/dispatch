package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.darkryh.dispatch.initializr.resources.JetBrainsMono_Bold
import io.github.darkryh.dispatch.initializr.resources.JetBrainsMono_Medium
import io.github.darkryh.dispatch.initializr.resources.JetBrainsMono_Regular
import io.github.darkryh.dispatch.initializr.resources.Res
import org.jetbrains.compose.resources.Font

/**
 * The web app's color palette — the visual twin of the library's `DispatchTheme.Dark` (ANSI roles
 * mapped to a GitHub-dark / iTerm scheme). Bright cyan is the one saturated signature accent.
 */
object Term {
    val background = Color(0xFF0F1216) // near-black CRT canvas (matches index.html)
    val backgroundDeep = Color(0xFF0A0D12) // behind the window / vignette
    val surface = Color(0xFF161B22) // panels, title bar, input troughs
    val surfaceRaised = Color(0xFF1C2129) // focused input fill
    val border = Color(0xFF30363D) // default hairline borders
    val borderMuted = Color(0xFF21262D) // inner dividers

    val primary = Color(0xFFE6EDF3) // brightWhite — values, headers
    val secondary = Color(0xFFADBAC7) // labels, body
    val muted = Color(0xFF6E7681) // gray — hints, comments, placeholders
    val faint = Color(0xFF484F58) // disabled / ghost

    val accent = Color(0xFF56D4DD) // brightCyan — THE signature
    val accentDim = Color(0xFF2B8B92) // cyan — shadow, secondary accent
    val accentGlow = Color(0x3356D4DD) // 20% cyan — focus halo / bloom

    val success = Color(0xFF3FB950) // green
    val warning = Color(0xFFD29922) // yellow
    val error = Color(0xFFF85149) // red
    val info = Color(0xFF58A6FF) // blue
    val code = Color(0xFFE3B341) // brightYellow — paths / .kt
    val cursor = Color(0xFF56D4DD)
}

/** Animation tempo, in milliseconds, kept in one place so motion stays coherent. */
object Tempo {
    const val CHAR_TYPE = 18 // per glyph in typewriter reveal
    const val CURSOR_BLINK = 530 // classic terminal blink half-period
    const val SPINNER_FRAME = 80 // braille spinner frame
    const val FOCUS_GLOW = 140 // focus underline grow
    const val BUILD_BUDGET = 1500 // total generate-animation cap (≈1.5s)
}

/**
 * Master accessibility switch for motion. Defaults to false; [io.github.darkryh.dispatch.initializr.Main]
 * seeds it from `prefers-reduced-motion` and an in-UI toggle can flip it. When true, every animation
 * resolves to its final frame immediately.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/** The bundled JetBrains Mono family — provided ambiently so the whole UI is monospace. */
val LocalTermFonts = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

/** Build the JetBrains Mono [FontFamily] from the bundled resources (falls back to Monospace while loading). */
@Composable
fun rememberDispatchFonts(): FontFamily =
    FontFamily(
        Font(Res.font.JetBrainsMono_Regular, FontWeight.Normal),
        Font(Res.font.JetBrainsMono_Medium, FontWeight.Medium),
        Font(Res.font.JetBrainsMono_Bold, FontWeight.Bold),
    )
