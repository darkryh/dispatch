package com.ead.dispatch.sample.presentation.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.BorderStyle
import com.ead.dispatch.runtime.util.toCliPathString
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Text
import com.github.ajalt.colormath.model.Oklab
import com.github.ajalt.colormath.model.SRGB
import com.github.ajalt.colormath.transform.interpolator
import com.github.ajalt.colormath.transform.sequence
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import java.nio.file.Paths

/**
 * Header component for the chat screen.
 *
 * Displays the app title, instructions, and a separator line.
 * Uses [LocalTerminalWidth] for proper formatting.
 */
@Dispatchable
fun ChatHeader() {
    val workingDir = Paths.get(System.getProperty("user.dir"))


    Panel(
        borderStyle = BorderStyle.Rounded,
        titleStyle = rgb("#787C81"),
    ) {
        Text(bannerTitle())
        Row {
            Text("⠀⠀model: ", style = rgb("#6F7279"))
            Text("terminal library (v0.0.1)", style = rgb("#FFFFFF"))
        }
        Row {
            Text("⠀⠀directory: ", style = rgb("#6F7279"))
            Text(workingDir.toCliPathString(), style = rgb("#FFFFFF"))
        }
    }
}

private val shadowColor = rgb("#24218c")

private fun bannerTitle(): String {
    val title = """
    ██████╗ ██╗███████╗██████╗  █████╗ ████████╗ ██████╗██╗  ██╗    ⠀
    ██╔══██╗██║██╔════╝██╔══██╗██╔══██╗╚══██╔══╝██╔════╝██║  ██║    
    ██║  ██║██║███████╗██████╔╝███████║   ██║   ██║     ███████║    
    ██║  ██║██║╚════██║██╔═══╝ ██╔══██║   ██║   ██║     ██╔══██║    
    ██████╔╝██║███████║██║     ██║  ██║   ██║   ╚██████╗██║  ██║    
    ╚═════╝ ╚═╝╚══════╝╚═╝     ╚═╝  ╚═╝   ╚═╝    ╚═════╝╚═╝  ╚═╝    
""".trim('\n')
    return buildString {
        for (line in title.lineSequence()) {
            val trimmedLine = line.trimEnd()
            val lerp = Oklab.interpolator {
                stop(SRGB("#f5d547"))
                stop(SRGB("#6b2fb9"))
            }.sequence(trimmedLine.length)
            trimmedLine.asSequence().zip(lerp).forEach { (c, color) ->
                append(TextColors.color(color)(c.toString()))
            }
            append("\n")
        }
    }
        .replace(Regex("""[╔═╗║╚╝]""")) { shadowColor(it.value) }
}
