package com.ead.dispatch.widget

import com.github.ajalt.mordant.rendering.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileChangePreviewTest {
    @Test
    fun `compute page info clamps selected index and reports page count`() {
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "",
                afterText = (1..95).joinToString("\n") { "line $it" },
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.FULL,
                pageIndex = 99,
                pageSizeRows = 20,
            )

        val info = computeFileChangePageInfo(state)

        assertEquals(5, info.pageCount)
        assertEquals(4, info.selectedPageIndex)
    }

    @Test
    fun `display rows page zero starts from first line in full mode`() {
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "",
                afterText = (1..30).joinToString("\n") { "line $it" },
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.FULL,
                pageIndex = 0,
                pageSizeRows = 10,
            )

        val pageRows = resolveDisplayRows(state, resolveFocusedRows(state, resolveRows(state)))
        val firstVisible = pageRows.first().line?.text
        val lastVisible = pageRows.last().line?.text

        assertEquals("line 1", firstVisible)
        assertEquals("line 10", lastVisible)
    }

    @Test
    fun `added-first focus shows added hunk with collapsed boundaries`() {
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "old line 1\nold line 2\nold line 3",
                afterText = "old line 1\nnew line 2\nold line 3",
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.ADDED_FIRST,
                contextLines = 0,
                showCollapsedUnchanged = true,
            )

        val focused = resolveFocusedRows(state, resolveRows(state))

        assertTrue(focused.any { it.isCollapsed })
        assertTrue(focused.any { it.line?.kind == LineChangeKind.ADDED })
    }

    @Test
    fun `deleted-only focus falls back to changed hunks when no pure deletion exists`() {
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "a\nb\nc",
                afterText = "a\nx\nc",
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.DELETED_ONLY,
                contextLines = 1,
            )

        val focused = resolveFocusedRows(state, resolveRows(state))

        assertTrue(focused.any { it.line?.kind == LineChangeKind.ADDED })
        assertTrue(focused.any { it.line?.kind == LineChangeKind.DELETED })
    }

    @Test
    fun `added-first focus includes all added hunks with collapsed gap marker`() {
        val state =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "a\nb\nc\nd\ne\nf",
                afterText = "a\nB\nc\nd\nE\nf",
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.ADDED_FIRST,
                contextLines = 0,
                showCollapsedUnchanged = true,
            )

        val focused = resolveFocusedRows(state, resolveRows(state))
        val addedRows = focused.filter { it.line?.kind == LineChangeKind.ADDED }

        assertEquals(2, addedRows.size)
        assertTrue(focused.any { it.isCollapsed })
    }

    @Test
    fun `markdown preview renders raw markdown source`() {
        val lines =
            renderLines(width = 80) {
                FileChangePreview(
                    state =
                        FileChangePreviewState(
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
        val lines =
            renderLines(width = 80) {
                FileChangePreview(
                    state =
                        FileChangePreviewState(
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
        val lines =
            renderLines(width = 80) {
                FileChangePreview(
                    state =
                        FileChangePreviewState(
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
        val styles =
            FileChangePreviewStyles(
                addedBackground = TextStyle(dim = true),
                deletedBackground = TextStyle(bold = true),
                pendingBackground = TextStyle(underline = true),
            )

        val deleted =
            ResolvedFileChangeRow(
                line =
                    FileChangeLine(
                        kind = LineChangeKind.DELETED,
                        oldLineNumber = 1,
                        newLineNumber = null,
                        text = "gone",
                    ),
                approvalState = RowApprovalState.PENDING,
            )
        val addedPending =
            ResolvedFileChangeRow(
                line =
                    FileChangeLine(
                        kind = LineChangeKind.ADDED,
                        oldLineNumber = null,
                        newLineNumber = 1,
                        text = "new",
                    ),
                approvalState = RowApprovalState.PENDING,
            )
        val addedNoApproval =
            ResolvedFileChangeRow(
                line =
                    FileChangeLine(
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

    @Test
    fun `applied replacement preview keeps added lines visible in viewport`() {
        val before = "Open on the salvage ship's deck."
        val after = (1..80).joinToString("\n") { index -> "New draft line $index with enough words to wrap in narrow width." }
        val lines =
            renderLines(width = 78) {
                FileChangePreview(
                    state =
                        FileChangePreviewState(
                            fileType = PreviewFileType.MARKDOWN,
                            beforeText = before,
                            afterText = after,
                            nowEpochMillis = 1_000L,
                            focusMode = ChangeFocusMode.ADDED_FIRST,
                            contextLines = 3,
                        ),
                    showHeader = false,
                    showStats = true,
                    maxVisibleRows = 22,
                )
            }

        assertTrue(lines.any { it.contains("+ New draft line") || it.contains("+") })
    }
}
