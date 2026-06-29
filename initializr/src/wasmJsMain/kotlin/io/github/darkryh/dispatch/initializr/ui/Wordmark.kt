package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The "DISPATCH" block figlet (ANSI-Shadow style). Rendered as fixed monospace lines, accent-colored. */
private val DISPATCH_FIGLET =
    listOf(
        "██████╗ ██╗███████╗██████╗  █████╗ ████████╗ ██████╗██╗  ██╗",
        "██╔══██╗██║██╔════╝██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██║  ██║",
        "██║  ██║██║███████╗██████╔╝███████║   ██║   ██║     ███████║",
        "██║  ██║██║╚════██║██╔═══╝ ██╔══██║   ██║   ██║     ██╔══██║",
        "██████╔╝██║███████║██║     ██║  ██║   ██║   ╚██████╗██║  ██║",
        "╚═════╝ ╚═╝╚══════╝╚═╝     ╚═╝  ╚═╝   ╚═╝    ╚═════╝╚═╝  ╚═╝",
    )

/** The DISPATCH wordmark with a subtle accent-brightness flicker. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val fonts = LocalTermFonts.current
    val flicker = rememberAccentFlicker()
    Column(modifier = modifier.graphicsLayer { alpha = flicker }) {
        DISPATCH_FIGLET.forEach { line ->
            Text(
                text = line,
                color = Term.accent,
                fontFamily = fonts,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                lineHeight = 14.sp,
            )
        }
    }
}
