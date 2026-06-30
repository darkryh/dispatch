package io.github.darkryh.dispatch.render

import io.github.darkryh.dispatch.render.AnsiGolden.CL
import io.github.darkryh.dispatch.render.AnsiGolden.CR
import io.github.darkryh.dispatch.render.AnsiGolden.LF
import io.github.darkryh.dispatch.render.AnsiGolden.delta
import io.github.darkryh.dispatch.render.AnsiGolden.recordingRenderer
import io.github.darkryh.dispatch.render.AnsiGolden.trailingRowClears
import io.github.darkryh.dispatch.render.AnsiGolden.visualizeEscapes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P0.1 — Golden ANSI byte-stream edge cases: ANSI-styled blank lines (isDisplayBlank /
 * ansiEscapeLength), active-area grow/shrink cursor arithmetic, empty/zero-height frames, and
 * width/height coercion to the 40x10 floor. All expectations are built from [AnsiCodes] and derived
 * by tracing `TerminalRenderer.kt`.
 */
class GoldenAnsiEdgeCaseTest {
    @BeforeTest
    fun diagnosticsMustBeUnset() {
        // Goldens assume DISPATCH_DIAGNOSTICS_FILE is unset; diagnostics never touch the terminal,
        // so emitted bytes are identical regardless, but we pin the assumption. We do NOT set it.
        assertFalse(
            RenderDiagnostics.isEnabled,
            "DISPATCH_DIAGNOSTICS_FILE must be unset for byte-exact goldens",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // ANSI-styled blank trailing rows are trimmed by isDisplayBlank (SGR runs skipped by
    // ansiEscapeLength), so handoff targets the last *visibly* non-blank row.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `styled-blank active rows are treated as blank for shell handoff`() {
        val (renderer, recorder) = recordingRenderer()
        val esc = ""
        // A line whose only visible characters are spaces, wrapped in real CSI SGR runs.
        val styledBlank = esc + "[48;2;54;60;70m    " + esc + "[49m" + esc + "[0m"

        renderer.updateActiveArea(listOf("Input", styledBlank, styledBlank))
        renderer.markVisibleContentHeight(10)
        val before = recorder.output()

        renderer.handoffToShellPrompt()
        val out = delta(recorder, before)

        // Trace: trailingBlankLineCount()=2 (both styled rows are display-blank) ->
        // targetRow = (10 - 2).coerceAtLeast(1).coerceAtMost(24) = 8.
        val expected = AnsiCodes.moveTo(8, 1) + AnsiCodes.CLEAR_LINE + LF
        assertEquals(expected, out, visualizeEscapes(out))
        assertTrue(out.endsWith(LF))
    }

    // ---------------------------------------------------------------------------------------------
    // Active-area GROW (1 -> 3): no leading moveUp (oldLineCount==1), all new rows painted, no shrink.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `active area grow from 1 to 3 lines paints new rows without moving up`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf("L1"))
        val before = recorder.output()

        renderer.updateActiveArea(listOf("L1", "L2", "L3"))
        val out = delta(recorder, before)

        // Trace: oldLineCount=1 -> moveToActiveAreaTop emits nothing (needs >1). forceRedraw=true ->
        // idx0 \r CLEAR_LINE L1 \n, idx1 \r CLEAR_LINE L2 \n, idx2 \r CLEAR_LINE L3 (no \n).
        // newLineCount(3) !in 1..<oldLineCount(1) -> no trailing moveUp.
        val expected =
            CR + CL + "L1" + LF +
                CR + CL + "L2" + LF +
                CR + CL + "L3"
        assertEquals(expected, out, visualizeEscapes(out))
        assertFalse(out.contains(AnsiCodes.moveUp(1)), "growing must not move the cursor up")
    }

    // ---------------------------------------------------------------------------------------------
    // Active-area SHRINK (3 -> 1): moveUp(2) to the top, blank the vacated rows, moveUp(2) back up.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `active area shrink from 3 to 1 line clears vacated rows and re-centers cursor`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf("L1", "L2", "L3"))
        val before = recorder.output()

        renderer.updateActiveArea(listOf("X1"))
        val out = delta(recorder, before)

        // Trace: oldLineCount=3 -> moveToActiveAreaTop emits moveUp(2). maxLineCount=3, forceRedraw:
        // idx0 \r CLEAR_LINE X1 \n; idx1 newLine==null -> \r CLEAR_LINE \n; idx2 newLine==null ->
        // \r CLEAR_LINE (no \n). newLineCount(1) in 1..<oldLineCount(3) -> moveCursorAfterShrink
        // emits moveUp(3-1)=moveUp(2).
        val expected =
            AnsiCodes.moveUp(2) +
                CR + CL + "X1" + LF +
                CR + CL + LF +
                CR + CL +
                AnsiCodes.moveUp(2)
        assertEquals(expected, out, visualizeEscapes(out))
    }

    // ---------------------------------------------------------------------------------------------
    // Empty / zero-height frames.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `appendScrollingContent with empty list emits nothing`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf(">in"))
        val before = recorder.output()

        renderer.appendScrollingContent(emptyList())

        // Trace: appendScrollingContent returns early on an empty list -> zero bytes.
        assertEquals("", delta(recorder, before))
    }

    @Test
    fun `rewriteViewport with empty content clears the whole screen to the floor height`() {
        val (renderer, recorder) = recordingRenderer()
        val before = recorder.output()

        renderer.rewriteViewport(scrollingLines = emptyList(), activeLines = emptyList())
        val out = delta(recorder, before)

        // Trace: clearScrollback defaults true -> CLEAR_SCROLLBACK + CURSOR_HOME + CLEAR_TO_END
        // (full visible-screen wipe so a scrolled previous screen cannot ghost). contentLineCount=0
        // -> no body. clearViewportRowsAfter(0): firstBlankRow=1 -> clear rows 1..24. No final moveTo
        // (contentLineCount==0).
        val expected =
            AnsiCodes.CLEAR_SCROLLBACK + AnsiCodes.CURSOR_HOME + AnsiCodes.CLEAR_TO_END +
                trailingRowClears(1, 24)
        assertEquals(expected, out, visualizeEscapes(out))
        assertFalse(out.contains(AnsiCodes.CLEAR_SCREEN))
    }

    // ---------------------------------------------------------------------------------------------
    // Width/height coercion: a sub-floor recorder (20x5) is clamped to the 40x10 floor, and the
    // trailing-row erase reaches the floor height (10), not the recorder height (5).
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `sub-floor terminal coerces to 40 by 10 and clears rows down to the floor`() {
        val (renderer, recorder) = recordingRenderer(width = 20, height = 5)

        assertEquals(40, renderer.terminalWidth, "width must coerce up to the 40 floor")
        assertEquals(10, renderer.terminalHeight, "height must coerce up to the 10 floor")

        val before = recorder.output()
        renderer.rewriteViewport(
            scrollingLines = listOf("H1"),
            activeLines = listOf(">in"),
            clearScrollback = false,
        )
        val out = delta(recorder, before)

        // Trace: contentLineCount=2 -> firstBlankRow=3; terminalHeight is the coerced floor (10), so
        // trailing clears span rows 3..10; final moveTo(2.coerceAtMost(10),1)=moveTo(2,1).
        val expected =
            AnsiCodes.CURSOR_HOME +
                CR + CL + "H1" + LF +
                CR + CL + ">in" +
                trailingRowClears(3, 10) +
                AnsiCodes.moveTo(2, 1)
        assertEquals(expected, out, visualizeEscapes(out))
        // The erase reaches the floor row, proving height coercion (recorder height was only 5).
        assertTrue(out.contains(AnsiCodes.moveTo(10, 1)))
        assertFalse(out.contains(AnsiCodes.moveTo(11, 1)))
    }
}
