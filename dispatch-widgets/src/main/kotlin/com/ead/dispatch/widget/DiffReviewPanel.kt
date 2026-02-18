package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

data class DiffReviewAction(
    val label: String,
)

data class DiffReviewPanelState(
    val title: String,
    val subtitle: String? = null,
    val statusLine: String? = null,
    val previewState: FileChangePreviewState,
    val pagesFocused: Boolean = false,
    val actions: List<DiffReviewAction> = emptyList(),
    val selectedActionIndex: Int = 0,
    val actionsFocused: Boolean = false,
)

@Dispatchable
fun DiffReviewPanel(
    state: DiffReviewPanelState,
    maxVisibleRows: Int,
    compact: Boolean,
    onPageCountResolved: (Int) -> Unit = {},
    pageTitle: String = "Pages",
    actionsTitle: String = "Actions",
    actionsKeyHints: List<KeyHint> = emptyList(),
    defaultKeyHints: List<KeyHint> = emptyList(),
    previewStyles: FileChangePreviewStyles = FileChangePreviewStyles(),
) {
    val pageInfo = computeFileChangePageInfo(state.previewState)
    val selectedPage = pageInfo.selectedPageIndex
    onPageCountResolved(pageInfo.pageCount)

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
                    text = "$it · page ${selectedPage + 1}/${pageInfo.pageCount}",
                    style = rgb("#8FA2B8"),
                )
            }
            HorizontalDivider(modifier = Modifier.fillMaxWidth())

            FileChangePreview(
                state = state.previewState,
                styles = previewStyles,
                showHeader = !compact,
                showStats = true,
                showLegend = !compact,
                maxVisibleRows = maxVisibleRows,
            )

            Spacer(Modifier.height(1))
            Text(
                text = pageTitle,
                style = rgb("#A3D9E5") + TextStyle(bold = true),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val maxVisibleIndicators = 7
                val startPage = (selectedPage - maxVisibleIndicators / 2).coerceAtLeast(0)
                    .coerceAtMost((pageInfo.pageCount - maxVisibleIndicators).coerceAtLeast(0))
                val endPageExclusive = (startPage + maxVisibleIndicators).coerceAtMost(pageInfo.pageCount)
                for (page in startPage until endPageExclusive) {
                    val isSelected = state.pagesFocused && page == selectedPage
                    val style = if (isSelected) {
                        rgb("#FFFFFF") + rgb("#1F3F6B").bg + TextStyle(bold = true)
                    } else {
                        rgb("#C3D1E6")
                    }
                    val text = " ${page + 1} "
                    Text(text = text, style = style)
                    Text(text = " ", style = rgb("#8FA2B8"))
                }
                if (endPageExclusive < pageInfo.pageCount) {
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
                        val style = if (isSelected) {
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
