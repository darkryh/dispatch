package io.github.darkryh.dispatch.vt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ESC = "\u001B"

/**
 * Self-tests for the screen model.
 *
 * The rest of this module reports on Dispatch by claiming to know what the terminal showed. That
 * claim is only worth what these tests are worth, so they are deliberately concrete: exact cursor
 * positions, exact grid contents, exact scrollback.
 */
class VtScreenTest {
    private fun run(width: Int, height: Int, vararg stream: String): VtParser {
        val parser = VtParser(VtScreen(width, height))
        stream.forEach { parser.feed(it) }
        return parser
    }

    @Test
    fun `writes text at the cursor`() {
        val p = run(10, 3, "hi")
        assertEquals("hi        ", p.screen.rowText(0))
        assertEquals(2, p.screen.cursorCol)
    }

    @Test
    fun `absolute cursor positioning is one-based on the wire and zero-based internally`() {
        val p = run(10, 5, "$ESC[3;5Hx")
        assertEquals(2, p.screen.cursorRow)
        assertEquals("    x     ", p.screen.rowText(2))
    }

    @Test
    fun `writing the last column defers the wrap until the next character`() {
        val p = run(4, 3, "abcd")
        // Cursor parks on the last column with the wrap pending — it has NOT moved to row 1.
        assertEquals(0, p.screen.cursorRow)
        assertTrue(p.screen.pendingWrap)
        assertEquals("abcd", p.screen.rowText(0))

        p.feed("e")
        assertEquals(1, p.screen.cursorRow)
        assertEquals("e   ", p.screen.rowText(1))
    }

    @Test
    fun `erase in line clears the whole row`() {
        val p = run(6, 2, "abcdef", "$ESC[1;1H", "$ESC[2K")
        assertTrue(p.screen.isRowBlank(0))
    }

    @Test
    fun `erase to end of display clears every row below the cursor`() {
        val p = run(4, 3, "aaaa\r\nbbbb\r\ncccc", "$ESC[2;1H", "$ESC[J")
        assertEquals("aaaa", p.screen.rowText(0))
        assertTrue(p.screen.isRowBlank(1))
        assertTrue(p.screen.isRowBlank(2))
    }

    @Test
    fun `line feed at the bottom scrolls and pushes the top row into scrollback`() {
        val p = run(4, 2, "aa\r\nbb\r\ncc")
        assertEquals(1, p.screen.scrollback.size)
        assertEquals("aa  ", p.screen.scrollback[0].joinToString("") { it.toChar() })
        assertEquals("bb  ", p.screen.rowText(0))
        assertEquals("cc  ", p.screen.rowText(1))
    }

    @Test
    fun `erase saved lines destroys scrollback and is counted`() {
        val p = run(4, 2, "aa\r\nbb\r\ncc")
        assertEquals(1, p.screen.scrollback.size)
        p.feed("$ESC[3J")
        assertEquals(0, p.screen.scrollback.size)
        assertEquals(1, p.screen.scrollbackClearCount)
        assertEquals(1, p.operations.count { it.kind == VtOp.Kind.ERASE_SCROLLBACK })
    }

    @Test
    fun `a cell painted with a background colour is not blank`() {
        val p = run(4, 1, "$ESC[41m ")
        assertFalse(p.screen.cellAt(0, 0).isBlank, "a space on a painted background is visible")
        assertFalse(p.screen.isRowBlank(0))
    }

    @Test
    fun `truecolor sgr is parsed and applied`() {
        val p = run(4, 1, "$ESC[38;2;12;34;56mX")
        assertEquals("rgb(12,34,56)", p.screen.cellAt(0, 0).style.foreground)
    }

    @Test
    fun `sgr reset clears all attributes`() {
        val p = run(4, 1, "$ESC[1;31mA${ESC}[0mB")
        assertTrue(p.screen.cellAt(0, 0).style.bold)
        assertEquals(CellStyle.DEFAULT, p.screen.cellAt(0, 1).style)
    }

    @Test
    fun `cursor visibility follows DECTCEM`() {
        val p = run(4, 1, "$ESC[?25l")
        assertFalse(p.screen.cursorVisible)
        p.feed("$ESC[?25h")
        assertTrue(p.screen.cursorVisible)
    }

    @Test
    fun `synchronized output brackets are tracked`() {
        val p = run(4, 1, "$ESC[?2026h")
        assertTrue(p.screen.synchronizedUpdate)
        p.feed("$ESC[?2026l")
        assertFalse(p.screen.synchronizedUpdate)
        assertEquals(1, p.operations.count { it.kind == VtOp.Kind.SYNC_BEGIN })
        assertEquals(1, p.operations.count { it.kind == VtOp.Kind.SYNC_END })
    }

    @Test
    fun `alternate screen and scroll regions are recorded`() {
        val p = run(4, 4, "$ESC[?1049h", "$ESC[2;3r")
        assertTrue(p.screen.alternateScreen)
        assertEquals(1..2, p.screen.scrollRegion)
        assertEquals(1, p.operations.count { it.kind == VtOp.Kind.ALT_SCREEN_ENTER })
        assertEquals(1, p.operations.count { it.kind == VtOp.Kind.SCROLL_REGION_SET })
    }

    @Test
    fun `a double width glyph occupies two columns`() {
        val p = run(6, 1, "你好")
        assertEquals(4, p.screen.cursorCol)
        assertTrue(p.screen.cellAt(0, 1).continuation)
        assertFalse(p.screen.cellAt(0, 0).continuation)
    }

    @Test
    fun `a row of double width glyphs exceeding the width wraps implicitly`() {
        // 3 CJK glyphs need 6 columns; the screen has 4.
        val p = run(4, 2, "你好世")
        assertTrue(p.screen.implicitWraps > 0, "expected an implicit wrap, got ${p.screen.implicitWraps}")
    }

    @Test
    fun `an escape sequence split across two feeds is still parsed as one`() {
        val parser = VtParser(VtScreen(10, 3))
        parser.feed("$ESC[3")
        parser.feed(";5Hx")
        assertEquals(2, parser.screen.cursorRow)
        assertEquals("    x     ", parser.screen.rowText(2))
    }

    @Test
    fun `a split escape sequence produces no phantom text`() {
        // This is the property the whole harness depends on: chunk boundaries must not corrupt the
        // parse, or every "torn frame" finding would be an artifact of the measurement.
        val whole = VtParser(VtScreen(20, 4))
        whole.feed("$ESC[2;1H${ESC}[2Khello${ESC}[0m")

        for (split in 1 until 24) {
            val stream = "$ESC[2;1H${ESC}[2Khello${ESC}[0m"
            if (split >= stream.length) break
            val piecewise = VtParser(VtScreen(20, 4))
            piecewise.feed(stream.substring(0, split))
            piecewise.feed(stream.substring(split))
            assertEquals(
                whole.screen.snapshot().rows,
                piecewise.screen.snapshot().rows,
                "split at $split produced a different screen",
            )
        }
    }

    @Test
    fun `damage tracking counts only rows actually written`() {
        val p = run(10, 5, "$ESC[1;1Habc", "$ESC[3;1Hdef")
        assertEquals(setOf(0, 2), p.screen.touchedRows)
        p.screen.resetDamage()
        assertEquals(emptySet(), p.screen.touchedRows)
    }

    @Test
    fun `dispatch's own vocabulary parses without unknown sequences`() {
        // Every escape AnsiCodes can emit.
        val p =
            run(
                20,
                5,
                "$ESC[H", "$ESC[?25l", "$ESC[?25h", "$ESC[s", "$ESC[u",
                "$ESC[2J", "$ESC[3J", "$ESC[2K", "$ESC[J",
                "$ESC[5;3H", "$ESC[1A", "$ESC[1B", "$ESC[2C", "$ESC[2D",
                "$ESC[1S", "$ESC[1T", "$ESC[0m",
            )
        assertEquals(emptyList(), p.unknownSequences)
    }
}
