package io.github.darkryh.dispatch.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the incremental path in [TerminalRenderer.rewriteViewport].
 *
 * This is the path that decides whether moving a selection one row costs one row of output or a
 * whole screen of it. Every other golden test in this module happens to exercise only the full
 * repaint, because they call `rewriteViewport` once per renderer and the diff needs a previous
 * frame to compare against.
 */
class ViewportDiffTest {
    private fun renderer() = AnsiGolden.recordingRenderer(width = 40, height = 10)

    @Test
    fun `the first rewrite has nothing to diff against and repaints in full`() {
        val (renderer, recorder) = renderer()
        renderer.rewriteViewport(
            scrollingLines = listOf("a".padEnd(40), "b".padEnd(40)),
            activeLines = listOf("c".padEnd(40)),
            clearScrollback = false,
        )
        val out = recorder.output()
        // The full path erases each row it draws; the diff path never does.
        assertTrue(out.contains(AnsiCodes.CLEAR_LINE), "first paint should take the full path")
    }

    @Test
    fun `a second rewrite emits only the rows that changed`() {
        val (renderer, recorder) = renderer()
        val first = listOf("row0".padEnd(40), "row1".padEnd(40), "row2".padEnd(40))
        renderer.rewriteViewport(first, activeLines = emptyList(), clearScrollback = false)

        val before = recorder.output()
        val second = listOf("row0".padEnd(40), "CHANGED".padEnd(40), "row2".padEnd(40))
        renderer.rewriteViewport(second, activeLines = emptyList(), clearScrollback = false)
        val delta = AnsiGolden.delta(recorder, before)

        assertTrue(delta.contains("CHANGED"), "the changed row must be painted")
        assertFalse(delta.contains("row0"), "an unchanged row must not be repainted")
        assertFalse(delta.contains("row2"), "an unchanged row must not be repainted")
        // Row 1 (0-based) is physical row 2.
        assertTrue(delta.contains(AnsiCodes.moveTo(2, 1)), "the changed row is addressed absolutely")
    }

    @Test
    fun `an unchanged frame emits no row output at all`() {
        val (renderer, recorder) = renderer()
        val lines = listOf("stable".padEnd(40), "rows".padEnd(40))
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = false)

        val before = recorder.output()
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = false)
        val delta = AnsiGolden.delta(recorder, before)

        assertFalse(delta.contains("stable"), "identical content must not be repainted")
        assertFalse(delta.contains("rows"), "identical content must not be repainted")
    }

    @Test
    fun `the diff path does not erase rows it overwrites`() {
        // Lines are padded to the full terminal width by the layout engine, so writing the line
        // overwrites the row completely. The erase was redundant, and it was what turned a torn
        // frame into a visibly blank row instead of stale text.
        val (renderer, recorder) = renderer()
        val first = listOf("aaaa".padEnd(40), "bbbb".padEnd(40))
        renderer.rewriteViewport(first, activeLines = emptyList(), clearScrollback = false)

        val before = recorder.output()
        renderer.rewriteViewport(
            listOf("aaaa".padEnd(40), "cccc".padEnd(40)),
            activeLines = emptyList(),
            clearScrollback = false,
        )
        val delta = AnsiGolden.delta(recorder, before)
        assertFalse(delta.contains(AnsiCodes.CLEAR_LINE), "diffed rows are overwritten, not erased")
    }

    @Test
    fun `a shrinking viewport erases the rows it no longer occupies`() {
        val (renderer, recorder) = renderer()
        renderer.rewriteViewport(
            listOf("one".padEnd(40), "two".padEnd(40), "three".padEnd(40)),
            activeLines = emptyList(),
            clearScrollback = false,
        )
        val before = recorder.output()
        renderer.rewriteViewport(
            listOf("one".padEnd(40)),
            activeLines = emptyList(),
            clearScrollback = false,
        )
        val delta = AnsiGolden.delta(recorder, before)
        assertTrue(delta.contains(AnsiCodes.moveTo(2, 1) + AnsiCodes.CLEAR_LINE), "row 2 vacated")
        assertTrue(delta.contains(AnsiCodes.moveTo(3, 1) + AnsiCodes.CLEAR_LINE), "row 3 vacated")
    }

    @Test
    fun `erasing the screen forces a full repaint`() {
        val (renderer, recorder) = renderer()
        val lines = listOf("x".padEnd(40), "y".padEnd(40))
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = false)

        val before = recorder.output()
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = true)
        val delta = AnsiGolden.delta(recorder, before)

        assertTrue(delta.contains(AnsiCodes.CLEAR_SCROLLBACK), "a transition wipes history")
        assertTrue(delta.contains(AnsiCodes.CLEAR_TO_END), "a transition erases the visible screen")
        assertTrue(delta.contains("x"), "and repaints everything, unchanged or not")
    }

    @Test
    fun `invalidating the cache forces the next rewrite back to the full path`() {
        val (renderer, recorder) = renderer()
        val lines = listOf("p".padEnd(40), "q".padEnd(40))
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = false)
        renderer.invalidateViewportDiff()

        val before = recorder.output()
        renderer.rewriteViewport(lines, activeLines = emptyList(), clearScrollback = false)
        val delta = AnsiGolden.delta(recorder, before)
        assertTrue(delta.contains("p"), "with no cache, identical content is repainted in full")
    }

    @Test
    fun `every frame is wrapped in the synchronized output guard`() {
        val (renderer, recorder) = renderer()
        renderer.rewriteViewport(
            listOf("a".padEnd(40)),
            activeLines = emptyList(),
            clearScrollback = false,
        )
        val out = recorder.output()
        assertTrue(out.startsWith(AnsiCodes.SYNC_BEGIN), "frame must open the sync guard")
        assertTrue(out.endsWith(AnsiCodes.SYNC_END), "frame must close the sync guard")
        assertEquals(1, countOf(out, AnsiCodes.SYNC_BEGIN), "exactly one guard per frame")
        assertEquals(1, countOf(out, AnsiCodes.SYNC_END), "exactly one guard per frame")
    }

    private fun countOf(
        haystack: String,
        needle: String,
    ): Int {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count += 1
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }
}
