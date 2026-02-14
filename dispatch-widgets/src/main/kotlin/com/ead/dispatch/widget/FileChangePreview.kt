package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Supported file types for [FileChangePreview].
 */
enum class PreviewFileType {
    TEXT,
    MARKDOWN,
}

/**
 * Story/workflow overlay configuration for line approvals.
 *
 * Pending ranges target line numbers in the **after** text (1-based, inclusive).
 */
data class FileChangeApprovalConfig(
    val pendingRanges: List<PendingLineRange>,
    val expiryMillis: Long = DEFAULT_EXPIRY_MILLIS,
) {
    init {
        require(expiryMillis > 0) { "expiryMillis must be > 0" }
    }

    companion object {
        const val DEFAULT_EXPIRY_MILLIS: Long = 7L * 24L * 60L * 60L * 1000L
    }
}

/**
 * Pending range metadata for approval workflows.
 */
data class PendingLineRange(
    val startLine: Int,
    val endLine: Int,
    val changedAtEpochMillis: Long,
) {
    init {
        require(startLine > 0) { "startLine must be > 0" }
        require(endLine >= startLine) { "endLine must be >= startLine" }
    }
}

/**
 * Input state for [FileChangePreview].
 */
data class FileChangePreviewState(
    val filePath: String? = null,
    val fileType: PreviewFileType,
    val beforeText: String,
    val afterText: String,
    val approval: FileChangeApprovalConfig? = null,
    val nowEpochMillis: Long,
)

/**
 * Computed summary metrics for a preview.
 */
data class FileChangeStats(
    val totalLines: Int,
    val addedLines: Int,
    val deletedLines: Int,
    val modifiedLines: Int,
    val pendingLines: Int,
)

/**
 * Styling for [FileChangePreview].
 */
data class FileChangePreviewStyles(
    val headerStyle: TextStyle? = rgb("#E6EAF0") + TextStyle(bold = true),
    val metaStyle: TextStyle? = rgb("#A7B2BF"),
    val statsStyle: TextStyle? = rgb("#8A95A5"),
    val gutterNumberStyle: TextStyle? = rgb("#6E7681"),
    val gutterMarkerStyle: TextStyle? = rgb("#4E5561"),
    val contentStyle: TextStyle? = rgb("#E6EAF0"),
    val addedBackground: TextStyle = rgb("#1E4D31"),
    val deletedBackground: TextStyle = rgb("#5A2323"),
    val pendingBackground: TextStyle = rgb("#1F3F6B"),
    val dividerChar: Char = '─',
    val legendAdded: String = "+ added",
    val legendDeleted: String = "- deleted",
    val legendPending: String = "~ pending approval",
)

/**
 * Render a line-based file change preview with a diff-style line gutter.
 *
 * V1 scope:
 * - plain text and markdown sources (markdown shown as raw source lines)
 * - read-only preview
 * - line-level diff computed from [FileChangePreviewState.beforeText] and [FileChangePreviewState.afterText]
 */
@Dispatchable
fun FileChangePreview(
    state: FileChangePreviewState,
    modifier: Modifier = Modifier,
    styles: FileChangePreviewStyles = FileChangePreviewStyles(),
    showHeader: Boolean = true,
    showStats: Boolean = true,
    showLegend: Boolean = false,
    maxVisibleRows: Int? = null,
    scrollState: ScrollState = rememberScrollState(),
) {
    require(maxVisibleRows == null || maxVisibleRows > 0) { "maxVisibleRows must be > 0 when specified" }

    val rows = resolveRows(state)
    val stats = calculateStats(rows, state.afterText)
    val oldDigits = rows.maxOfOrNull { (it.line.oldLineNumber ?: 0).toString().length }?.coerceAtLeast(1) ?: 1
    val newDigits = rows.maxOfOrNull { (it.line.newLineNumber ?: 0).toString().length }?.coerceAtLeast(1) ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            val fileId = state.filePath?.takeIf { it.isNotBlank() } ?: "(unnamed)"
            Text(
                text = "File: $fileId",
                style = styles.headerStyle,
            )
            Text(
                text = "Type: ${state.fileType.name.lowercase()}",
                style = styles.metaStyle,
            )
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), char = styles.dividerChar)
        }

        if (showStats) {
            Text(
                text = formatStats(stats),
                style = styles.statsStyle,
            )
        }

        if (showLegend) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(styles.legendAdded, style = styles.metaStyle)
                Text("   ", style = styles.metaStyle)
                Text(styles.legendDeleted, style = styles.metaStyle)
                Text("   ", style = styles.metaStyle)
                Text(styles.legendPending, style = styles.metaStyle)
            }
        }

        if (showHeader || showStats || showLegend) {
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), char = styles.dividerChar)
        }

        val bodyModifier = if (maxVisibleRows != null) {
            Modifier.fillMaxWidth().height(maxVisibleRows)
        } else {
            Modifier.fillMaxWidth()
        }

        ScrollableList(
            items = rows,
            modifier = bodyModifier,
            scrollState = scrollState,
        ) { row ->
            val rowText = formatDataRow(row.line, maxOf(oldDigits, newDigits), styles)
            val rowBackground = resolveRowBackground(row, state.approval != null, styles)
            if (rowBackground != null) {
                Background(
                    modifier = Modifier.fillMaxWidth(),
                    style = BackgroundStyle.Fill(
                        fill = rowBackground,
                        paddingHorizontal = 0,
                        paddingVertical = 0,
                    )
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = rowText,
                            style = null,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = rowText,
                        style = null,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        }
    }
}

/**
 * Compute [FileChangeStats] for this preview state.
 */
fun FileChangePreviewState.computeStats(): FileChangeStats =
    calculateStats(resolveRows(this), afterText)

internal enum class LineChangeKind {
    UNCHANGED,
    ADDED,
    DELETED,
}

internal data class FileChangeLine(
    val kind: LineChangeKind,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
)

internal enum class RowApprovalState {
    NONE,
    PENDING,
    EXPIRED,
}

internal data class ResolvedFileChangeRow(
    val line: FileChangeLine,
    val approvalState: RowApprovalState,
)

internal fun resolveRows(state: FileChangePreviewState): List<ResolvedFileChangeRow> {
    val lines = diffLines(state.beforeText, state.afterText)
    val approval = state.approval ?: return lines.map { ResolvedFileChangeRow(it, RowApprovalState.NONE) }
    return lines.map { line ->
        if (line.kind == LineChangeKind.DELETED) {
            return@map ResolvedFileChangeRow(line, RowApprovalState.NONE)
        }
        val targetLine = line.newLineNumber
        if (targetLine == null) {
            return@map ResolvedFileChangeRow(line, RowApprovalState.NONE)
        }
        val changedAt = newestPendingTimestampForLine(targetLine, approval.pendingRanges)
            ?: return@map ResolvedFileChangeRow(line, RowApprovalState.NONE)
        val isExpired = state.nowEpochMillis - changedAt >= approval.expiryMillis
        if (isExpired) {
            ResolvedFileChangeRow(line, RowApprovalState.EXPIRED)
        } else {
            ResolvedFileChangeRow(line, RowApprovalState.PENDING)
        }
    }
}

internal fun diffLines(beforeText: String, afterText: String): List<FileChangeLine> {
    val before = splitFileLines(beforeText)
    val after = splitFileLines(afterText)

    val lcs = lcsTable(before, after)
    val rows = mutableListOf<FileChangeLine>()
    var i = 0
    var j = 0
    var oldLine = 1
    var newLine = 1

    while (i < before.size && j < after.size) {
        if (before[i] == after[j]) {
            rows += FileChangeLine(
                kind = LineChangeKind.UNCHANGED,
                oldLineNumber = oldLine++,
                newLineNumber = newLine++,
                text = before[i],
            )
            i++
            j++
            continue
        }

        if (lcs[i + 1][j] >= lcs[i][j + 1]) {
            rows += FileChangeLine(
                kind = LineChangeKind.DELETED,
                oldLineNumber = oldLine++,
                newLineNumber = null,
                text = before[i],
            )
            i++
        } else {
            rows += FileChangeLine(
                kind = LineChangeKind.ADDED,
                oldLineNumber = null,
                newLineNumber = newLine++,
                text = after[j],
            )
            j++
        }
    }

    while (i < before.size) {
        rows += FileChangeLine(
            kind = LineChangeKind.DELETED,
            oldLineNumber = oldLine++,
            newLineNumber = null,
            text = before[i],
        )
        i++
    }

    while (j < after.size) {
        rows += FileChangeLine(
            kind = LineChangeKind.ADDED,
            oldLineNumber = null,
            newLineNumber = newLine++,
            text = after[j],
        )
        j++
    }

    return rows
}

internal fun lcsTable(before: List<String>, after: List<String>): Array<IntArray> {
    val rows = before.size
    val cols = after.size
    val table = Array(rows + 1) { IntArray(cols + 1) }

    for (i in rows - 1 downTo 0) {
        for (j in cols - 1 downTo 0) {
            table[i][j] = if (before[i] == after[j]) {
                table[i + 1][j + 1] + 1
            } else {
                maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
    }

    return table
}

internal fun splitFileLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    return text
        .split('\n')
        .map { it.removeSuffix("\r") }
}

internal fun newestPendingTimestampForLine(
    lineNumber: Int,
    ranges: List<PendingLineRange>,
): Long? {
    var newest: Long? = null
    for (range in ranges) {
        if (lineNumber in range.startLine..range.endLine) {
            newest = maxOf(newest ?: Long.MIN_VALUE, range.changedAtEpochMillis)
        }
    }
    return newest
}

internal fun calculateStats(
    rows: List<ResolvedFileChangeRow>,
    afterText: String,
): FileChangeStats {
    val added = rows.count { it.line.kind == LineChangeKind.ADDED }
    val deleted = rows.count { it.line.kind == LineChangeKind.DELETED }
    val pending = rows.count { it.approvalState == RowApprovalState.PENDING }
    val modified = countModifiedLines(rows.map { it.line.kind })
    return FileChangeStats(
        totalLines = splitFileLines(afterText).size,
        addedLines = added,
        deletedLines = deleted,
        modifiedLines = modified,
        pendingLines = pending,
    )
}

internal fun countModifiedLines(kinds: List<LineChangeKind>): Int {
    var modified = 0
    var index = 0
    while (index < kinds.size) {
        if (kinds[index] == LineChangeKind.UNCHANGED) {
            index++
            continue
        }

        var added = 0
        var deleted = 0
        while (index < kinds.size && kinds[index] != LineChangeKind.UNCHANGED) {
            when (kinds[index]) {
                LineChangeKind.ADDED -> added++
                LineChangeKind.DELETED -> deleted++
                LineChangeKind.UNCHANGED -> Unit
            }
            index++
        }
        modified += minOf(added, deleted)
    }
    return modified
}

internal fun resolveRowBackground(
    row: ResolvedFileChangeRow,
    hasApprovalConfig: Boolean,
    styles: FileChangePreviewStyles,
): TextStyle? {
    if (row.line.kind == LineChangeKind.DELETED) return styles.deletedBackground
    if (hasApprovalConfig) {
        return if (row.approvalState == RowApprovalState.PENDING) styles.pendingBackground else null
    }
    return if (row.line.kind == LineChangeKind.ADDED) styles.addedBackground else null
}

internal fun formatDataRow(
    line: FileChangeLine,
    lineDigits: Int,
    styles: FileChangePreviewStyles,
): String {
    val visibleLineNumber = line.newLineNumber ?: line.oldLineNumber
    val numberPart = visibleLineNumber?.toString()?.padStart(lineDigits) ?: " ".repeat(lineDigits)
    val marker = when (line.kind) {
        LineChangeKind.ADDED -> "+"
        LineChangeKind.DELETED -> "-"
        LineChangeKind.UNCHANGED -> " "
    }
    val numberStyled = applyStyle(numberPart, styles.gutterNumberStyle)
    val markerStyled = applyStyle(marker, styles.gutterMarkerStyle)
    val contentStyled = applyStyle(line.text, styles.contentStyle)
    return numberStyled + " " + markerStyled + " " + contentStyled
}

internal fun formatStats(stats: FileChangeStats): String =
    "lines=${stats.totalLines}  +${stats.addedLines}  -${stats.deletedLines}  ~${stats.modifiedLines}  pending=${stats.pendingLines}"

internal fun applyStyle(text: String, style: TextStyle?): String = style?.invoke(text) ?: text
