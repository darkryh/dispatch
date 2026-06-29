package com.ead.dispatch.render

import com.ead.dispatch.render.AnsiGolden.CL
import com.ead.dispatch.render.AnsiGolden.CR
import com.ead.dispatch.render.AnsiGolden.LF
import com.ead.dispatch.render.AnsiGolden.delta
import com.ead.dispatch.render.AnsiGolden.recordingRenderer
import com.ead.dispatch.render.AnsiGolden.trailingRowClears
import com.ead.dispatch.render.AnsiGolden.visualizeEscapes
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * P0.1 — Golden ANSI byte-stream regression suite (the five canonical scenarios).
 *
 * Each expected string is built from [AnsiCodes] constants in-code (refactor-safe, self-documenting)
 * and compared byte-exact against the real renderer output. The expectations were derived by tracing
 * the exact code paths in `TerminalRenderer.kt` (see per-test comments); they are NOT captured from a
 * run. The recorder is the standard 80x24 TRUECOLOR fixture shared with the existing tests.
 */
class GoldenAnsiStreamTest {
    @BeforeTest
    fun diagnosticsMustBeUnset() {
        // Goldens assume DISPATCH_DIAGNOSTICS_FILE is unset (the production default). Diagnostics
        // only ever write to a file sidecar, never to the terminal, so even when enabled the emitted
        // bytes are identical — but we pin the assumption here for documentation and determinism.
        // We deliberately do NOT set the env var (it is read once at RenderDiagnostics class-init).
        assertFalse(
            RenderDiagnostics.isEnabled,
            "DISPATCH_DIAGNOSTICS_FILE must be unset for byte-exact goldens",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario A — Append-scrolling: print-once / append-only; active area cleared-before + restored.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `A - appendScrollingContent clears active, commits each line once, restores active`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf(">in"))
        val before = recorder.output()

        renderer.appendScrollingContent(listOf("M1", "M2"))
        val out = delta(recorder, before)

        // Trace appendScrollingContent -> clearActiveAreaInto (one active line: \r CLEAR_LINE, no
        // moveUp) + appendLines (each line + \n) + restoreActiveAreaInto (\r CLEAR_LINE >in, no \n).
        val expected =
            CR + CL + // clear the single active line in place
                "M1" + LF + "M2" + LF + // commit both scrolling lines, each terminated once
                CR + CL + ">in" // restore the active line (last line: no trailing \n)
        assertEquals(expected, out, visualizeEscapes(out))

        // print-once: "M1" is committed exactly once in the delta.
        assertEquals(1, Regex("M1").findAll(out).count(), "M1 must be committed exactly once")

        // append-only: a second append must NOT re-emit already-committed content.
        val before2 = recorder.output()
        renderer.appendScrollingContent(listOf("M3", "M4"))
        val out2 = delta(recorder, before2)
        assertFalse(out2.contains("M1"), "committed content must never be re-emitted")
        assertFalse(out2.contains("M2"), "committed content must never be re-emitted")
        assertTrue(out2.contains("M3") && out2.contains("M4"))
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario B — Viewport rewrite (keep): draw-before-clear; same-screen never emits 2J.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `B - rewriteViewport keep emits no scrollback, draws then clears trailing rows`() {
        val (renderer, recorder) = recordingRenderer()
        val before = recorder.output()

        renderer.rewriteViewport(
            scrollingLines = listOf("H1", "H2"),
            activeLines = listOf(">in", "stat"),
            clearScrollback = false,
        )
        val out = delta(recorder, before)

        // Trace: no CLEAR_SCROLLBACK (clearScrollback=false) -> CURSOR_HOME -> per line
        // \r CLEAR_LINE line with \n between (last line has no \n) -> clear rows 5..24 -> moveTo(4,1).
        val expected =
            AnsiCodes.CURSOR_HOME +
                CR + CL + "H1" + LF +
                CR + CL + "H2" + LF +
                CR + CL + ">in" + LF +
                CR + CL + "stat" + // 4th / last content line: no trailing \n
                trailingRowClears(5, 24) + // contentLineCount=4 -> firstBlankRow=5, height=24
                AnsiCodes.moveTo(4, 1) // moveTo(contentLineCount.coerceAtMost(height), 1)
        assertEquals(expected, out, visualizeEscapes(out))

        assertFalse(out.contains(AnsiCodes.CLEAR_SCROLLBACK), "keep-rewrite must not clear scrollback")
        assertFalse(out.contains(AnsiCodes.CLEAR_SCREEN), "same-screen must never emit 2J")
        assertTrue(out.startsWith(AnsiCodes.CURSOR_HOME))
        assertFalse(out.endsWith(LF), "viewport rewrite must not end with a trailing newline")
        // draw-before-clear: content is emitted before the trailing-row erase begins.
        assertTrue(out.indexOf("H1") < out.indexOf(AnsiCodes.moveTo(5, 1)))
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario C — Active-area update: zero scrollback emission; single change repaints all siblings.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `C - updateActiveArea with one changed line moves up and repaints every sibling`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf("A", "B"))
        val before = recorder.output()

        renderer.updateActiveArea(listOf("A*", "B"))
        val out = delta(recorder, before)

        // Trace: oldLineCount=2 -> moveToActiveAreaTop emits moveUp(1). forceRedraw=true (content
        // changed) so appendActiveAreaUpdates repaints BOTH siblings: idx0 \r CLEAR_LINE A* \n,
        // idx1 \r CLEAR_LINE B (no \n). No shrink -> no trailing moveUp.
        val expected =
            AnsiCodes.moveUp(1) +
                CR + CL + "A*" + LF +
                CR + CL + "B"
        assertEquals(expected, out, visualizeEscapes(out))

        // single-change repaints the whole active block: the unchanged sibling "B" is re-emitted.
        assertTrue(out.contains("A*"))
        assertTrue(out.contains("B"))
        // zero scrollback emission: an active-area update never commits a bare \n-terminated line.
        assertFalse(out.endsWith(LF), "active-area update must not commit a scrollback line")

        // identical-content call is a no-op (no bytes emitted).
        val before2 = recorder.output()
        renderer.updateActiveArea(listOf("A*", "B"))
        assertEquals("", delta(recorder, before2), "identical content must emit nothing")
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario D — Resize / full rewrite: screen-transition full repaint; 3J is the ONLY scrollback
    // code, never 2J; content-before-clear ordering.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `D - rewriteViewport clearScrollback leads with 3J + home then full trailing erase`() {
        val (renderer, recorder) = recordingRenderer()
        val before = recorder.output()

        renderer.rewriteViewport(
            scrollingLines = listOf("H1"),
            activeLines = listOf(">in"),
            clearScrollback = true,
        )
        val out = delta(recorder, before)

        // Trace: CLEAR_SCROLLBACK (clearScrollback=true) -> CURSOR_HOME -> \r CLEAR_LINE H1 \n ->
        // \r CLEAR_LINE >in (last, no \n) -> clear rows 3..24 -> moveTo(2,1).
        val expected =
            AnsiCodes.CLEAR_SCROLLBACK + AnsiCodes.CURSOR_HOME +
                CR + CL + "H1" + LF +
                CR + CL + ">in" +
                trailingRowClears(3, 24) + // contentLineCount=2 -> firstBlankRow=3, height=24
                AnsiCodes.moveTo(2, 1)
        assertEquals(expected, out, visualizeEscapes(out))

        assertTrue(
            out.startsWith(AnsiCodes.CLEAR_SCROLLBACK + AnsiCodes.CURSOR_HOME),
            "screen transition must lead with 3J then cursor-home",
        )
        assertFalse(out.contains(AnsiCodes.CLEAR_SCREEN), "3J is the only scrollback code, never 2J")
        // CLEAR_SCROLLBACK appears exactly once and nowhere but the lead.
        assertEquals(1, Regex(Regex.escape(AnsiCodes.CLEAR_SCROLLBACK)).findAll(out).count())
        // content-before-clear: the visible content precedes the trailing-row erase (no blank frame).
        assertTrue(out.indexOf("H1") < out.indexOf(AnsiCodes.moveTo(3, 1)))
    }

    // ---------------------------------------------------------------------------------------------
    // Scenario E — Shell handoff: cursor left on a fresh line under visible content; ends with \n.
    // ---------------------------------------------------------------------------------------------
    @Test
    fun `E - handoffToShellPrompt moves to visible-content row, clears it, emits newline`() {
        val (renderer, recorder) = recordingRenderer()

        renderer.updateActiveArea(listOf("L1", "L2", "L3"))
        renderer.markVisibleContentHeight(12)
        val before = recorder.output()

        renderer.handoffToShellPrompt()
        val out = delta(recorder, before)

        // Trace: no trailing blank lines -> targetRow = (12 - 0).coerceAtLeast(1).coerceAtMost(24)
        // = 12. Emits moveTo(12,1) + CLEAR_LINE + "\n".
        val expected = AnsiCodes.moveTo(12, 1) + AnsiCodes.CLEAR_LINE + LF
        assertEquals(expected, out, visualizeEscapes(out))
        assertTrue(out.endsWith(LF), "handoff must leave the cursor on a fresh line")
    }
}
