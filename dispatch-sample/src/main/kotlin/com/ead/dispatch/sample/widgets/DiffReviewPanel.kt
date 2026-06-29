@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.DiffColors
import com.ead.dispatch.widget.DiffFocus
import com.ead.dispatch.widget.DiffRow
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.ScrollableList
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.rememberFileDiff
import com.ead.dispatch.widget.rememberScrollState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

data class DiffReviewAction(
    val label: String,
)

data class DiffReviewPanelState(
    val title: String,
    val subtitle: String? = null,
    val statusLine: String? = null,
    val before: String,
    val after: String,
    val path: String? = null,
    val focus: DiffFocus = DiffFocus.Full,
    val contextLines: Int = 3,
    val pageIndex: Int = 0,
    val pageSizeRows: Int = 20,
    val pending: Set<Int> = emptySet(),
    val pagesFocused: Boolean = false,
    val actions: List<DiffReviewAction> = emptyList(),
    val selectedActionIndex: Int = 0,
    val actionsFocused: Boolean = false,
)

@Composable
fun DiffReviewPanel(
    state: DiffReviewPanelState,
    maxVisibleRows: Int,
    compact: Boolean,
    onPageCountResolved: (Int) -> Unit = {},
    pageTitle: String = "Pages",
    actionsTitle: String = "Actions",
    actionsKeyHints: List<KeyHint> = emptyList(),
    defaultKeyHints: List<KeyHint> = emptyList(),
    colors: DiffColors = DiffColors(),
) {
    val diff = rememberFileDiff(state.before, state.after, focus = state.focus, contextLines = state.contextLines)
    val page = diff.page(state.pageIndex, state.pageSizeRows)
    val selectedPage = page.pageIndex
    val pageCount = page.pageCount
    onPageCountResolved(pageCount)

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = state.title,
                style = rgb("#A3D9E5") + TextStyle(bold = true),
            )
            state.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = rgb("#B9CEE6"),
                )
            }
            state.statusLine?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = "$it · page ${selectedPage + 1}/$pageCount",
                    style = rgb("#8FA2B8"),
                )
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            if (!compact && state.path != null) {
                Text(text = "File: ${state.path}", style = colors.header)
            }
            Text(text = formatDiffStats(diff.stats), style = colors.stats)
            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            DiffPageBody(
                rows = page.rows,
                maxVisibleRows = maxVisibleRows,
                colors = colors,
                pending = state.pending,
            )

            Spacer(Modifier.height(1))
            Text(
                text = pageTitle,
                style = rgb("#A3D9E5") + TextStyle(bold = true),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val maxVisibleIndicators = 7
                val startPage =
                    (selectedPage - maxVisibleIndicators / 2)
                        .coerceAtLeast(0)
                        .coerceAtMost((pageCount - maxVisibleIndicators).coerceAtLeast(0))
                val endPageExclusive = (startPage + maxVisibleIndicators).coerceAtMost(pageCount)
                for (pageNumber in startPage until endPageExclusive) {
                    val isSelected = state.pagesFocused && pageNumber == selectedPage
                    val style =
                        if (isSelected) {
                            rgb("#FFFFFF") + rgb("#1F3F6B").bg + TextStyle(bold = true)
                        } else {
                            rgb("#C3D1E6")
                        }
                    val text = " ${pageNumber + 1} "
                    Text(text = text, style = style)
                    Text(text = " ", style = rgb("#8FA2B8"))
                }
                if (endPageExclusive < pageCount) {
                    Text(text = "…", style = rgb("#8FA2B8"))
                }
            }

            if (state.actions.isNotEmpty()) {
                Spacer(Modifier.height(1))
                Text(
                    text = actionsTitle,
                    style = rgb("#A3D9E5") + TextStyle(bold = true),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    state.actions.forEachIndexed { index, action ->
                        val isSelected = state.actionsFocused && index == state.selectedActionIndex
                        val style =
                            if (isSelected) {
                                rgb("#FFFFFF") + rgb("#1F3F6B").bg + TextStyle(bold = true)
                            } else {
                                rgb("#C3D1E6")
                            }
                        val text = if (isSelected) "> ${action.label} <" else "  ${action.label}  "
                        Text(text = text, style = style)
                        if (index < state.actions.lastIndex) {
                            Text(text = "   ", style = rgb("#8FA2B8"))
                        }
                    }
                }
                if (actionsKeyHints.isNotEmpty()) {
                    Spacer(Modifier.height(1))
                    KeyHintBar(
                        hints = actionsKeyHints,
                        keyStyle = rgb("#8FA2B8"),
                        descriptionStyle = rgb("#8FA2B8"),
                        separatorStyle = rgb("#8FA2B8"),
                    )
                }
            } else if (defaultKeyHints.isNotEmpty()) {
                Spacer(Modifier.height(1))
                KeyHintBar(
                    hints = defaultKeyHints,
                    keyStyle = rgb("#8FA2B8"),
                    descriptionStyle = rgb("#8FA2B8"),
                    separatorStyle = rgb("#8FA2B8"),
                )
            }
        }
        Spacer(Modifier.width(2))
    }
}

@Composable
private fun DiffPageBody(
    rows: List<DiffRow>,
    maxVisibleRows: Int,
    colors: DiffColors,
    pending: Set<Int>,
) {
    val lineDigits =
        rows
            .mapNotNull { it.newLineNumber ?: it.oldLineNumber }
            .maxOfOrNull { it.toString().length }
            ?.coerceAtLeast(1)
            ?: 1
    val scrollState = rememberScrollState()
    ScrollableList(
        items = rows,
        modifier = Modifier.fillMaxWidth().height(maxVisibleRows),
        scrollState = scrollState,
    ) { row ->
        if (row.isCollapsed) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "…", modifier = Modifier.fillMaxWidth(), style = rgb("#A7B2BF"))
            }
            return@ScrollableList
        }
        val number = (row.newLineNumber ?: row.oldLineNumber)?.toString()?.padStart(lineDigits) ?: " ".repeat(lineDigits)
        val markerStyle =
            when (row.marker) {
                '+' -> rgb("#699862") + TextStyle(bold = true)
                '-' -> rgb("#6E3D37") + TextStyle(bold = true)
                else -> rgb("#4E5561")
            }
        val background: TextStyle? =
            when {
                row.marker == '-' -> colors.deleted
                row.newLineNumber != null && row.newLineNumber in pending -> rgb("#1F3F6B")
                row.marker == '+' -> colors.added
                else -> null
            }
        val contentStyle = if (background != null) colors.content + background.bg else colors.content
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = number, style = colors.gutter)
            Text(text = " ", style = null)
            Text(text = row.marker.toString(), style = markerStyle)
            Text(text = " ", style = null)
            Text(text = row.content, modifier = Modifier.fillMaxWidth(), style = contentStyle)
        }
    }
}

private fun formatDiffStats(stats: com.ead.dispatch.widget.DiffStats): String =
    "lines=${stats.totalLines}  +${stats.addedLines}  -${stats.deletedLines}  ~${stats.modifiedLines}"
