package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.terminal.Terminal
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.runtime.Composer
import io.github.darkryh.dispatch.runtime.KeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.LocalTerminalHeight
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.withComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The palette has to answer "which one am I about to run?" from colour alone.
 *
 * It did not. `label` and `selectedLabel` both defaulted to `#ffffff`, [CommandPalette]'s
 * `selectionIndicator` defaults to null, and the only remaining difference was two adjacent greys
 * on the description column (`#6F7279` against `#898D92`). A caller that took the defaults — the
 * obvious thing to do — got a list where every command looked identical.
 *
 * These render in TRUECOLOR (the widget's own [CommandPaletteTest] renders with [AnsiLevel.NONE],
 * which strips exactly what is under test here) and assert on the escape codes.
 */
class CommandPaletteSelectionStylingTest {
    private val commands =
        listOf(
            CommandOption(label = "exit", description = "Close the inspector", data = "exit"),
            CommandOption(label = "shutdown", description = "Stop the server and quit", data = "shutdown"),
            CommandOption(label = "help", description = "Show what each command does", data = "help"),
        )

    @Test
    fun `the default styles make the selected row look different from the others`() {
        val lines = render(commands)

        assertNotEquals(
            styleCodesOf(lines.selected),
            styleCodesOf(lines.unselected),
            "selected and unselected rows must not render with the same styling — with equal-length " +
                "labels and no indicator that leaves nothing at all to tell them apart",
        )
    }

    @Test
    fun `the default selected label is styled differently from an unselected one`() {
        val lines = render(commands)

        // Compare the styling that wraps the label itself, not the description: a caller can turn
        // descriptions off, and selection still has to be visible.
        assertNotEquals(
            stylePrecedingRun(lines.selected, "/exit"),
            stylePrecedingRun(lines.unselected, "/shutdown"),
            "selection has to be carried by the label, so it survives showDescriptions = false",
        )
    }

    @Test
    fun `selectedRowFill paints a full-width bar behind the selected row only`() {
        val lines =
            render(
                commands,
                styles = CommandPaletteTextStyles(selectedRowFill = rgb("#1F3A5F")),
            )

        assertTrue(
            BACKGROUND_CODE.containsMatchIn(lines.selected),
            "the selected row should carry a background colour, got: ${lines.selected.escaped()}",
        )
        assertFalse(
            BACKGROUND_CODE.containsMatchIn(lines.unselected),
            "an unselected row must stay unfilled, got: ${lines.unselected.escaped()}",
        )
        assertEquals(
            WIDTH,
            visibleWidthOf(lines.selected),
            "the bar has to span the full row, otherwise it reads as a coloured word rather than a highlight",
        )
    }

    @Test
    fun `selectedRowFill is off unless asked for`() {
        val lines = render(commands)

        assertFalse(
            BACKGROUND_CODE.containsMatchIn(lines.selected),
            "the fill is opt-in — turning it on by default would change the layout of every existing palette",
        )
    }

    @Test
    fun `the filled row does not grow the palette`() {
        val plain = renderAll(commands, CommandPaletteTextStyles())
        val filled = renderAll(commands, CommandPaletteTextStyles(selectedRowFill = rgb("#1F3A5F")))

        assertEquals(
            plain.size,
            filled.size,
            "a highlight bar must stay one line — a Surface with box padding would make it three",
        )
    }

    private class RenderedRows(
        val selected: String,
        val unselected: String,
    )

    private fun render(
        options: List<CommandOption<String>>,
        styles: CommandPaletteTextStyles = CommandPaletteTextStyles(),
    ): RenderedRows {
        val lines = renderAll(options, styles)
        return RenderedRows(
            selected = lines.first { it.contains("/exit") },
            unselected = lines.first { it.contains("/shutdown") },
        )
    }

    private fun renderAll(
        options: List<CommandOption<String>>,
        styles: CommandPaletteTextStyles,
    ): List<String> {
        val terminal =
            Terminal(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = WIDTH,
                height = 20,
                interactive = false,
            )
        val composer = Composer()
        withComposer(composer) {
            composer.startComposition()
            CompositionLocalProvider(
                LocalTerminal provides terminal,
                LocalTerminalWidth provides terminal.size.width,
                LocalTerminalHeight provides terminal.size.height,
                LocalKeyboardInterceptor provides KeyboardInterceptor(),
            ) {
                CommandPalette(
                    options = options,
                    inputValue = "/",
                    onOptionSelected = {},
                    onInputTransform = {},
                    textStyles = styles,
                    state = CommandPaletteState(),
                )
            }
            composer.endComposition()
        }
        val lines = composer.getRootNode()?.measure(Constraints.fixedWidth(WIDTH))?.lines ?: emptyList()
        composer.close()
        return lines
    }

    /** Every SGR sequence on the line, in order — the line's styling with its text removed. */
    private fun styleCodesOf(line: String): List<String> = SGR.findAll(line).map { it.value }.toList()

    /** The SGR sequence immediately before [text], i.e. the style that word is rendered in. */
    private fun stylePrecedingRun(
        line: String,
        text: String,
    ): String {
        val at = line.indexOf(text)
        if (at < 0) return ""
        return SGR
            .findAll(line.substring(0, at))
            .lastOrNull()
            ?.value
            .orEmpty()
    }

    private fun visibleWidthOf(line: String): Int = line.replace(SGR, "").length

    private fun String.escaped(): String = replace("", "\\e")

    private companion object {
        const val WIDTH = 80
        val SGR = Regex("\\[[0-9;]*m")

        /** `ESC[48;2;R;G;Bm` — a truecolor background. */
        val BACKGROUND_CODE = Regex("\\[[0-9;]*48;2;")
    }
}
