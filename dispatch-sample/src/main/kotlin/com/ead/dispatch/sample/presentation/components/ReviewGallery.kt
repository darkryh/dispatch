@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.sample.widgets.DiffReviewAction
import com.ead.dispatch.sample.widgets.DiffReviewPanel
import com.ead.dispatch.sample.widgets.DiffReviewPanelState
import com.ead.dispatch.sample.widgets.SectionHeader
import com.ead.dispatch.widget.DiffFocus
import com.ead.dispatch.widget.FileDiff
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

private val SAMPLE_BEFORE =
    listOf("""fun status() = "old"""", "println(status())").joinToString(System.lineSeparator())

private val SAMPLE_AFTER =
    listOf(
        """fun status() = "ready"""",
        "println(status())",
        """println("validated")""",
    ).joinToString(System.lineSeparator())

@Composable
internal fun ReviewGallery() {
    GalleryScreen("Review", "File diffs, paging, and approval action presentation") {
        SectionHeader("FileDiff")
        Text("+ added   - deleted   ~ pending approval", style = rgb("#A7B2BF"))
        FileDiff(
            before = SAMPLE_BEFORE,
            after = SAMPLE_AFTER,
            path = "sample.kt",
            focus = DiffFocus.Full,
            contextLines = 1,
            maxVisibleRows = 4,
        )
        SectionHeader("DiffReviewPanel")
        DiffReviewPanel(
            state =
                DiffReviewPanelState(
                    title = "Review pending edits",
                    subtitle = "sample.kt",
                    statusLine = "2 changed lines",
                    before = SAMPLE_BEFORE,
                    after = SAMPLE_AFTER,
                    path = "sample.kt",
                    focus = DiffFocus.Full,
                    contextLines = 1,
                    pageIndex = 0,
                    pageSizeRows = 3,
                    pagesFocused = true,
                    actions = listOf(DiffReviewAction("Approve"), DiffReviewAction("Reject")),
                    actionsFocused = true,
                ),
            maxVisibleRows = 3,
            compact = true,
        )
    }
}
