package io.github.darkryh.dispatch.initializr.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import io.github.darkryh.dispatch.initializr.generate.ProjectGenerator
import io.github.darkryh.dispatch.initializr.model.ProjectConfig
import io.github.darkryh.dispatch.initializr.platform.downloadZip
import io.github.darkryh.dispatch.initializr.zip.ZipArchive
import kotlinx.coroutines.delay

private sealed interface LogLine {
    val text: String

    data class Header(override val text: String) : LogLine

    data class Running(override val text: String) : LogLine

    data class Ok(override val text: String) : LogLine

    data class Done(override val text: String) : LogLine
}

/**
 * The hero "build log": the real zip is computed up front, then one line per generated file streams
 * in with a braille spinner flipping to a green `✓`, ending in a download. Theatrical but bounded
 * ([Tempo.BUILD_BUDGET]); under reduced motion it downloads immediately with no animation.
 */
@Composable
fun BuildConsole(
    config: ProjectConfig,
    onFinished: (zipName: String) -> Unit,
) {
    val fonts = LocalTermFonts.current
    val reduced = LocalReducedMotion.current
    val lines = remember { mutableStateListOf<LogLine>() }
    var inFlight by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val files = ProjectGenerator.generate(config)
        val zip = ZipArchive.create(files) // real work, instant — done before any theatrics
        val name = ProjectGenerator.zipName(config)

        if (reduced) {
            downloadZip(name, zip)
            onFinished(name)
            return@LaunchedEffect
        }

        lines += LogLine.Header("gradle :generate")
        val perLine = (Tempo.BUILD_BUDGET * 0.7 / files.size.coerceAtLeast(1)).toLong().coerceIn(28L, 90L)
        for (file in files.sortedBy { it.path }) {
            inFlight = true
            lines += LogLine.Running(file.path)
            delay(perLine)
            lines[lines.lastIndex] = LogLine.Ok(file.path)
        }
        inFlight = false
        delay(120)
        lines += LogLine.Done("done — downloading $name")
        delay(220)
        downloadZip(name, zip) // download fires only AFTER the animation resolves
        onFinished(name)
    }

    val frame = rememberSpinnerFrame(inFlight)
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            val glyph =
                when (line) {
                    is LogLine.Header -> "❯"
                    is LogLine.Running -> spinnerGlyph(frame)
                    is LogLine.Ok, is LogLine.Done -> "✓"
                }
            val color =
                when (line) {
                    is LogLine.Ok, is LogLine.Done -> Term.success
                    else -> Term.accent
                }
            Text("$glyph ${line.text}", color = color, fontFamily = fonts, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
