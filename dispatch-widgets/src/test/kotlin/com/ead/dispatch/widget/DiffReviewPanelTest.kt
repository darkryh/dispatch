package com.ead.dispatch.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiffReviewPanelTest {
    @Test
    fun `large content uses selected page and resolves page count`() {
        var resolvedPageCount = 0
        val previewState =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "",
                afterText = (1..95).joinToString("\n") { "line $it" },
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.FULL,
                pageIndex = 2,
                pageSizeRows = 20,
            )
        val panelState =
            DiffReviewPanelState(
                title = "Preview",
                statusLine = "status: applied",
                previewState = previewState,
                pagesFocused = true,
            )

        val lines =
            renderLines(width = 100, height = 80) {
                DiffReviewPanel(
                    state = panelState,
                    maxVisibleRows = 20,
                    compact = true,
                    onPageCountResolved = { resolvedPageCount = it },
                    defaultKeyHints =
                        listOf(
                            KeyHint("Ctrl+P", "focus"),
                            KeyHint("←/→", "page"),
                        ),
                )
            }

        assertEquals(5, resolvedPageCount)
        assertTrue(lines.any { it.contains("status: applied · page 3/5") })
        assertTrue(lines.any { it.contains("line 41") })
        assertTrue(lines.any { it.contains("line 60") })
    }

    @Test
    fun `actions strip and action hints are rendered when actions exist`() {
        val previewState =
            FileChangePreviewState(
                fileType = PreviewFileType.TEXT,
                beforeText = "old",
                afterText = "new",
                nowEpochMillis = 1_000L,
                focusMode = ChangeFocusMode.FULL,
                pageIndex = 0,
                pageSizeRows = 10,
            )
        val panelState =
            DiffReviewPanelState(
                title = "Preview",
                previewState = previewState,
                actions =
                    listOf(
                        DiffReviewAction("Approve Proposal"),
                        DiffReviewAction("Reject Proposal"),
                    ),
                selectedActionIndex = 1,
                actionsFocused = true,
            )

        val lines =
            renderLines(width = 100, height = 40) {
                DiffReviewPanel(
                    state = panelState,
                    maxVisibleRows = 8,
                    compact = true,
                    actionsKeyHints =
                        listOf(
                            KeyHint("Enter", "select"),
                            KeyHint("Esc", "input"),
                        ),
                )
            }

        assertTrue(lines.any { it.contains("Actions") })
        assertTrue(lines.any { it.contains("Approve Proposal") })
        assertTrue(lines.any { it.contains("Reject Proposal") })
        assertTrue(lines.any { it.contains("Enter") && it.contains("select") })
        assertTrue(lines.any { it.contains("Esc") && it.contains("input") })
    }
}
