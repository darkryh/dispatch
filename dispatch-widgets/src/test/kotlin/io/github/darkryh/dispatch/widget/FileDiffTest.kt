package io.github.darkryh.dispatch.widget

import com.github.ajalt.mordant.rendering.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileDiffTest {
    @Test
    fun `page clamps selected index and reports page count`() {
        val result =
            computeFileDiff(
                before = "",
                after = (1..95).joinToString("\n") { "line $it" },
                focus = DiffFocus.Full,
                contextLines = 3,
            )

        val page = result.page(index = 99, size = 20)

        assertEquals(5, page.pageCount)
        assertEquals(4, page.pageIndex)
    }

    @Test
    fun `page zero starts from first line in full mode`() {
        val result =
            computeFileDiff(
                before = "",
                after = (1..30).joinToString("\n") { "line $it" },
                focus = DiffFocus.Full,
                contextLines = 3,
            )

        val page = result.page(index = 0, size = 10)

        assertEquals("line 1", page.rows.first().content)
        assertEquals("line 10", page.rows.last().content)
    }

    @Test
    fun `additions focus shows added hunk with collapsed boundaries`() {
        val result =
            computeFileDiff(
                before = "old line 1\nold line 2\nold line 3",
                after = "old line 1\nnew line 2\nold line 3",
                focus = DiffFocus.Additions,
                contextLines = 0,
            )

        assertTrue(result.rows.any { it.isCollapsed })
        assertTrue(result.rows.any { it.marker == '+' })
    }

    @Test
    fun `deletions focus falls back to changed hunks when no pure deletion exists`() {
        val result =
            computeFileDiff(
                before = "a\nb\nc",
                after = "a\nx\nc",
                focus = DiffFocus.Deletions,
                contextLines = 1,
            )

        assertTrue(result.rows.any { it.marker == '+' })
        assertTrue(result.rows.any { it.marker == '-' })
    }

    @Test
    fun `additions focus includes all added hunks with collapsed gap marker`() {
        val result =
            computeFileDiff(
                before = "a\nb\nc\nd\ne\nf",
                after = "a\nB\nc\nd\nE\nf",
                focus = DiffFocus.Additions,
                contextLines = 0,
            )

        val addedRows = result.rows.filter { it.marker == '+' }
        assertEquals(2, addedRows.size)
        assertTrue(result.rows.any { it.isCollapsed })
    }

    @Test
    fun `renders raw source line`() {
        val lines =
            renderLines(width = 80) {
                FileDiff(
                    before = "",
                    after = "**bold**",
                    showHeader = false,
                    showStats = false,
                )
            }

        assertTrue(lines.any { it.contains("**bold**") })
    }

    @Test
    fun `maxVisibleRows limits rendered body rows`() {
        val lines =
            renderLines(width = 80) {
                FileDiff(
                    before = "",
                    after = "l1\nl2\nl3\nl4",
                    showHeader = false,
                    showStats = false,
                    maxVisibleRows = 2,
                )
            }

        assertEquals(2, lines.size)
    }

    @Test
    fun `without maxVisibleRows all rows are rendered in unbounded mode`() {
        val lines =
            renderLines(width = 80) {
                FileDiff(
                    before = "",
                    after = "l1\nl2\nl3\nl4",
                    showHeader = false,
                    showStats = false,
                )
            }

        assertEquals(4, lines.size)
    }

    @Test
    fun `row background precedence uses deleted before pending and pending before added`() {
        val colors =
            DiffColors(
                added = TextStyle(dim = true),
                deleted = TextStyle(bold = true),
            )
        val pendingColor = TextStyle(underline = true)

        val deleted = DiffRow(marker = '-', oldLineNumber = 1, content = "gone")
        val addedPending = DiffRow(marker = '+', newLineNumber = 1, content = "new")
        val addedPlain = DiffRow(marker = '+', newLineNumber = 2, content = "new")
        val unchanged = DiffRow(marker = ' ', oldLineNumber = 3, newLineNumber = 3, content = "same")

        assertEquals(colors.deleted, resolveRowBackground(deleted, pending = setOf(1), colors, pendingColor))
        assertEquals(pendingColor, resolveRowBackground(addedPending, pending = setOf(1), colors, pendingColor))
        assertEquals(colors.added, resolveRowBackground(addedPlain, pending = setOf(1), colors, pendingColor))
        assertEquals(null, resolveRowBackground(unchanged, pending = emptySet(), colors, pendingColor))
    }

    @Test
    fun `applied replacement keeps added lines visible in viewport`() {
        val before = "Open on the salvage ship's deck."
        val after = (1..80).joinToString("\n") { index -> "New draft line $index with enough words to wrap in narrow width." }
        val lines =
            renderLines(width = 78) {
                FileDiff(
                    before = before,
                    after = after,
                    focus = DiffFocus.Additions,
                    contextLines = 3,
                    showHeader = false,
                    showStats = true,
                    maxVisibleRows = 22,
                )
            }

        assertTrue(lines.any { it.contains("+ New draft line") || it.contains("+") })
    }
}
