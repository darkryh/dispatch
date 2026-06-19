package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileChangeDiffTest {
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
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "a\nb\nc",
                afterText = "a\nx\nc",
                nowEpochMillis = 10_000,
            )

        val stats = state.computeStats()
        assertEquals(1, stats.modifiedLines)
        assertEquals(1, stats.addedLines)
        assertEquals(1, stats.deletedLines)
    }

    @Test
    fun `formatted rows include padded dual gutters`() {
        val styles =
            FileChangePreviewStyles(
                headerStyle = null,
                metaStyle = null,
                statsStyle = null,
                gutterNumberStyle = null,
                gutterMarkerStyle = null,
                contentStyle = null,
            )
        val row =
            FileChangeLine(
                kind = LineChangeKind.ADDED,
                oldLineNumber = null,
                newLineNumber = 12,
                text = "hello",
            )

        val rendered =
            formatDataRow(
                line = row,
                lineDigits = 3,
                styles = styles,
            )

        assertTrue(rendered.startsWith(" 12 + "))
        assertTrue(rendered.endsWith("hello"))
    }

    @Test
    fun `pending expiry boundary becomes expired and not pending`() {
        val now = 8_000L
        val expiry = 1_000L
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "",
                afterText = "one",
                approval =
                    FileChangeApprovalConfig(
                        pendingRanges =
                            listOf(
                                PendingLineRange(
                                    startLine = 1,
                                    endLine = 1,
                                    changedAtEpochMillis = now - expiry,
                                ),
                            ),
                        expiryMillis = expiry,
                    ),
                nowEpochMillis = now,
            )

        val rows = resolveRows(state)
        assertEquals(RowApprovalState.EXPIRED, rows.single().approvalState)
        val stats = calculateStats(rows, state.afterText)
        assertEquals(0, stats.pendingLines)
    }
}
