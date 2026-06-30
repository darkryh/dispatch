package io.github.darkryh.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineDiffTest {
    @Test
    fun `empty to text creates added rows with new numbers`() {
        val rows =
            diffLines(
                beforeText = "",
                afterText = "alpha\nbeta",
            )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == LineChangeKind.ADDED })
        assertEquals(null, rows[0].oldLineNumber)
        assertEquals(1, rows[0].newLineNumber)
        assertEquals("alpha", rows[0].text)
        assertEquals(2, rows[1].newLineNumber)
    }

    @Test
    fun `text to empty creates deleted rows with old numbers`() {
        val rows =
            diffLines(
                beforeText = "alpha\nbeta",
                afterText = "",
            )

        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == LineChangeKind.DELETED })
        assertEquals(1, rows[0].oldLineNumber)
        assertNull(rows[0].newLineNumber)
        assertEquals("alpha", rows[0].text)
        assertEquals(2, rows[1].oldLineNumber)
    }

    @Test
    fun `middle insertion keeps line numbers stable`() {
        val rows =
            diffLines(
                beforeText = "a\nc",
                afterText = "a\nb\nc",
            )

        assertEquals(
            listOf(
                LineChangeKind.UNCHANGED,
                LineChangeKind.ADDED,
                LineChangeKind.UNCHANGED,
            ),
            rows.map { it.kind },
        )
        assertEquals(1, rows[0].oldLineNumber)
        assertEquals(1, rows[0].newLineNumber)
        assertEquals(2, rows[1].newLineNumber)
        assertEquals(2, rows[2].oldLineNumber)
        assertEquals(3, rows[2].newLineNumber)
    }

    @Test
    fun `middle deletion keeps line numbers stable`() {
        val rows =
            diffLines(
                beforeText = "a\nb\nc",
                afterText = "a\nc",
            )

        assertEquals(
            listOf(
                LineChangeKind.UNCHANGED,
                LineChangeKind.DELETED,
                LineChangeKind.UNCHANGED,
            ),
            rows.map { it.kind },
        )
        assertEquals(2, rows[1].oldLineNumber)
        assertNull(rows[1].newLineNumber)
        assertEquals(3, rows[2].oldLineNumber)
        assertEquals(2, rows[2].newLineNumber)
    }

    @Test
    fun `replacement is counted as modified`() {
        val rows = diffLines("a\nb\nc", "a\nx\nc").map { DiffRowInternal(it) }
        val stats = calculateStats(rows, "a\nx\nc")

        assertEquals(1, stats.modifiedLines)
        assertEquals(1, stats.addedLines)
        assertEquals(1, stats.deletedLines)
    }
}
