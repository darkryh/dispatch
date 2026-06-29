@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.sample.widgets.ChangeFocusMode
import com.ead.dispatch.sample.widgets.DiffReviewAction
import com.ead.dispatch.sample.widgets.DiffReviewPanel
import com.ead.dispatch.sample.widgets.DiffReviewPanelState
import com.ead.dispatch.sample.widgets.FileChangePreview
import com.ead.dispatch.sample.widgets.FileChangePreviewState
import com.ead.dispatch.sample.widgets.PreviewFileType
import com.ead.dispatch.sample.widgets.SectionHeader

@Composable
internal fun ReviewGallery() {
    val preview = samplePreviewState()
    GalleryScreen("Review", "File diffs, paging, and approval action presentation") {
        SectionHeader("FileChangePreview")
        FileChangePreview(
            state = preview,
            maxVisibleRows = 4,
            showLegend = true,
        )
        SectionHeader("DiffReviewPanel")
        DiffReviewPanel(
            state =
                DiffReviewPanelState(
                    title = "Review pending edits",
                    subtitle = "sample.kt",
                    statusLine = "2 changed lines",
                    previewState = preview.copy(pageIndex = 0, pageSizeRows = 3),
                    pagesFocused = true,
                    actions = listOf(DiffReviewAction("Approve"), DiffReviewAction("Reject")),
                    actionsFocused = true,
                ),
            maxVisibleRows = 3,
            compact = true,
        )
    }
}

private fun samplePreviewState(): FileChangePreviewState =
    FileChangePreviewState(
        filePath = "sample.kt",
        fileType = PreviewFileType.TEXT,
        beforeText = listOf("""fun status() = "old"""", "println(status())").joinToString(System.lineSeparator()),
        afterText =
            listOf(
                """fun status() = "ready"""",
                "println(status())",
                """println("validated")""",
            ).joinToString(System.lineSeparator()),
        nowEpochMillis = 0L,
        focusMode = ChangeFocusMode.FULL,
        contextLines = 1,
    )
