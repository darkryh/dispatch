package io.github.darkryh.dispatch.render

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ViewportTransitionCleanupReliabilityTest {
    @Test
    fun `transition from long history to short screen clears full viewport`() {
        val recorder =
            TerminalRecorder(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = 120,
                height = 40,
                supportsAnsiCursor = true,
            )
        val renderer = TerminalRenderer(Terminal(terminalInterface = recorder))

        val longHistory = List(30) { "history-line-${it + 1}" }
        renderer.rewriteViewport(
            scrollingLines = longHistory,
            activeLines = listOf("> chat input", "chat footer"),
            clearScrollback = true,
        )
        val beforeShortScreen = recorder.output()

        renderer.rewriteViewport(
            scrollingLines = listOf("story-anchor"),
            activeLines = listOf("✦ story input"),
            clearScrollback = true,
        )
        val delta = recorder.output().removePrefix(beforeShortScreen)

        assertTrue(delta.contains(AnsiCodes.CURSOR_HOME))
        assertTrue(delta.contains(AnsiCodes.moveTo(40, 1)))
        assertTrue(delta.contains(AnsiCodes.CLEAR_LINE))
        assertTrue(delta.contains("story-anchor"))
        assertTrue(delta.contains("✦ story input"))
        assertFalse(delta.contains(AnsiCodes.CLEAR_SCREEN))
        assertTrue(delta.indexOf("story-anchor") < delta.indexOf(AnsiCodes.moveTo(40, 1)))
    }
}
