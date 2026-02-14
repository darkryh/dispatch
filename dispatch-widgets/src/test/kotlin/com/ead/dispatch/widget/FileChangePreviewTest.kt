package com.ead.dispatch.widget

import com.github.ajalt.mordant.rendering.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileChangePreviewTest {

    @Test
    fun `markdown preview renders raw markdown source`() {
        val lines = renderLines(width = 80) {
            FileChangePreview(
                state = FileChangePreviewState(
                    fileType = PreviewFileType.MARKDOWN,
                    beforeText = "",
                    afterText = "**bold**",
                    nowEpochMillis = 1_000L,
                ),
                showHeader = false,
                showStats = false,
            )
        }

        assertTrue(lines.any { it.contains("**bold**") })
    }

    @Test
    fun `maxVisibleRows limits rendered body rows`() {
        val lines = renderLines(width = 80) {
            FileChangePreview(
                state = FileChangePreviewState(
                    fileType = PreviewFileType.TEXT,
                    beforeText = "",
                    afterText = "l1\nl2\nl3\nl4",
                    nowEpochMillis = 1_000L,
                ),
                showHeader = false,
                showStats = false,
                maxVisibleRows = 2,
            )
        }

        assertEquals(2, lines.size)
    }

    @Test
    fun `without maxVisibleRows all rows are rendered in unbounded mode`() {
        val lines = renderLines(width = 80) {
            FileChangePreview(
                state = FileChangePreviewState(
                    fileType = PreviewFileType.TEXT,
                    beforeText = "",
                    afterText = "l1\nl2\nl3\nl4",
                    nowEpochMillis = 1_000L,
                ),
                showHeader = false,
                showStats = false,
            )
        }

        assertEquals(4, lines.size)
    }

    @Test
    fun `row background precedence uses deleted before pending and pending before added`() {
        val styles = FileChangePreviewStyles(
            addedBackground = TextStyle(dim = true),
            deletedBackground = TextStyle(bold = true),
            pendingBackground = TextStyle(underline = true),
        )

        val deleted = ResolvedFileChangeRow(
            line = FileChangeLine(
                kind = LineChangeKind.DELETED,
                oldLineNumber = 1,
                newLineNumber = null,
                text = "gone",
            ),
            approvalState = RowApprovalState.PENDING,
        )
        val addedPending = ResolvedFileChangeRow(
            line = FileChangeLine(
                kind = LineChangeKind.ADDED,
                oldLineNumber = null,
                newLineNumber = 1,
                text = "new",
            ),
            approvalState = RowApprovalState.PENDING,
        )
        val addedNoApproval = ResolvedFileChangeRow(
            line = FileChangeLine(
                kind = LineChangeKind.ADDED,
                oldLineNumber = null,
                newLineNumber = 1,
                text = "new",
            ),
            approvalState = RowApprovalState.NONE,
        )

        assertEquals(styles.deletedBackground, resolveRowBackground(deleted, hasApprovalConfig = true, styles = styles))
        assertEquals(styles.pendingBackground, resolveRowBackground(addedPending, hasApprovalConfig = true, styles = styles))
        assertEquals(styles.addedBackground, resolveRowBackground(addedNoApproval, hasApprovalConfig = false, styles = styles))
        assertEquals(null, resolveRowBackground(addedNoApproval, hasApprovalConfig = true, styles = styles))
    }
}
