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

enum class ChangeFocusMode {
    ADDED_FIRST,
    DELETED_ONLY,
    FULL,
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
    val focusMode: ChangeFocusMode = ChangeFocusMode.ADDED_FIRST,
    val contextLines: Int = 3,
    val showCollapsedUnchanged: Boolean = true,
    val selectedHunkIndex: Int = 0,
    val pageIndex: Int? = null,
    val pageSizeRows: Int? = null,
)

data class FileChangePageInfo(
    val pageCount: Int,
    val selectedPageIndex: Int,
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
    require(state.contextLines >= 0) { "contextLines must be >= 0" }
    require(state.pageIndex == null || state.pageIndex >= 0) { "pageIndex must be >= 0 when specified" }
    require(state.pageSizeRows == null || state.pageSizeRows > 0) { "pageSizeRows must be > 0 when specified" }

    val allRows = resolveRows(state)
    val focusedRows = resolveFocusedRows(state, allRows)
    val rows = resolveDisplayRows(state, focusedRows)
    val stats = calculateStats(allRows, state.afterText)
    val oldDigits = rows
        .mapNotNull { it.line?.oldLineNumber }
        .maxOfOrNull { it.toString().length }
        ?.coerceAtLeast(1)
        ?: 1
    val newDigits = rows
        .mapNotNull { it.line?.newLineNumber }
        .maxOfOrNull { it.toString().length }
        ?.coerceAtLeast(1)
        ?: 1

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
            if (row.isCollapsed) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = applyStyle("…", styles.metaStyle),
                        modifier = Modifier.fillMaxWidth(),
                        style = null,
                    )
                }
                return@ScrollableList
            }
            val line = row.line ?: return@ScrollableList
            val rowBackground = resolveRowBackground(row, state.approval != null, styles)
            DiffPreviewRow(
                line = line,
                lineDigits = maxOf(oldDigits, newDigits),
                styles = styles,
                markerStyle = resolveMarkerStyle(line, styles),
                rowBackground = if (line.kind == LineChangeKind.UNCHANGED) null else rowBackground,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun computeFileChangePageInfo(state: FileChangePreviewState): FileChangePageInfo {
    val focusedRows = resolveFocusedRows(state, resolveRows(state))
    val pageSize = state.pageSizeRows
    if (pageSize == null) {
        return FileChangePageInfo(pageCount = 1, selectedPageIndex = 0)
    }
    val totalPages = ((focusedRows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val selected = state.pageIndex?.coerceIn(0, totalPages - 1) ?: 0
    return FileChangePageInfo(pageCount = totalPages, selectedPageIndex = selected)
}

@Dispatchable
private fun DiffPreviewRow(
    line: FileChangeLine,
    lineDigits: Int,
    styles: FileChangePreviewStyles,
    markerStyle: TextStyle?,
    rowBackground: TextStyle?,
    modifier: Modifier = Modifier,
) {
    val visibleLineNumber = line.newLineNumber ?: line.oldLineNumber
    val numberPart = visibleLineNumber?.toString()?.padStart(lineDigits) ?: " ".repeat(lineDigits)
    val marker = when (line.kind) {
        LineChangeKind.ADDED -> "+"
        LineChangeKind.DELETED -> "-"
        LineChangeKind.UNCHANGED -> " "
    }
    val contentStyle = mergeStyles(styles.contentStyle, normalizeRowFill(rowBackground))

    Row(modifier = modifier) {
        Text(text = numberPart, style = styles.gutterNumberStyle)
        Text(text = " ", style = null)
        Text(text = marker, style = markerStyle)
        Text(text = " ", style = null)
        Text(
            text = line.text,
            modifier = Modifier.fillMaxWidth(),
            style = contentStyle,
        )
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
    val line: FileChangeLine?,
    val approvalState: RowApprovalState,
    val isCollapsed: Boolean = false,
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

private data class DiffHunk(
    val startIndex: Int,
    val endIndex: Int,
    val hasAdded: Boolean,
    val hasDeleted: Boolean,
    val firstAddedIndex: Int?,
    val firstDeletedIndex: Int?,
)

internal fun resolveFocusedRows(
    state: FileChangePreviewState,
    allRows: List<ResolvedFileChangeRow>,
): List<ResolvedFileChangeRow> {
    if (state.focusMode == ChangeFocusMode.FULL) return allRows
    val hunks = extractHunks(allRows)
    if (hunks.isEmpty()) return allRows

    val targetHunks = when (state.focusMode) {
        ChangeFocusMode.ADDED_FIRST -> {
            val preferred = hunks.filter { it.hasAdded }
            if (preferred.isNotEmpty()) preferred else hunks
        }
        ChangeFocusMode.DELETED_ONLY -> {
            val preferred = hunks.filter { it.hasDeleted && !it.hasAdded }
            if (preferred.isNotEmpty()) preferred else hunks
        }
        ChangeFocusMode.FULL -> hunks
    }

    val mergedSegments = mutableListOf<IntRange>()
    targetHunks.sortedBy { it.startIndex }.forEach { hunk ->
        val anchorIndex = when (state.focusMode) {
            ChangeFocusMode.ADDED_FIRST -> hunk.firstAddedIndex ?: hunk.startIndex
            ChangeFocusMode.DELETED_ONLY -> hunk.firstDeletedIndex ?: hunk.startIndex
            ChangeFocusMode.FULL -> hunk.startIndex
        }
        val start = (anchorIndex - state.contextLines).coerceAtLeast(0)
        val end = (hunk.endIndex + state.contextLines).coerceAtMost(allRows.lastIndex)
        val candidate = start..end
        val last = mergedSegments.lastOrNull()
        if (last == null) {
            mergedSegments += candidate
        } else if (candidate.first <= last.last + 1) {
            mergedSegments[mergedSegments.lastIndex] = last.first..maxOf(last.last, candidate.last)
        } else {
            mergedSegments += candidate
        }
    }

    if (mergedSegments.isEmpty()) return allRows
    if (!state.showCollapsedUnchanged) {
        return mergedSegments.flatMap { allRows.subList(it.first, it.last + 1) }
    }

    val result = mutableListOf<ResolvedFileChangeRow>()
    mergedSegments.forEachIndexed { index, segment ->
        if (index == 0) {
            if (segment.first > 0) {
                result += ResolvedFileChangeRow(line = null, approvalState = RowApprovalState.NONE, isCollapsed = true)
            }
        } else {
            result += ResolvedFileChangeRow(line = null, approvalState = RowApprovalState.NONE, isCollapsed = true)
        }
        result += allRows.subList(segment.first, segment.last + 1)
    }
    if (mergedSegments.last().last < allRows.lastIndex) {
        result += ResolvedFileChangeRow(line = null, approvalState = RowApprovalState.NONE, isCollapsed = true)
    }
    return result
}

internal fun resolveDisplayRows(
    state: FileChangePreviewState,
    focusedRows: List<ResolvedFileChangeRow>,
): List<ResolvedFileChangeRow> {
    val pageSize = state.pageSizeRows ?: return focusedRows
    val pageCount = ((focusedRows.size + pageSize - 1) / pageSize).coerceAtLeast(1)
    val page = (state.pageIndex ?: 0).coerceIn(0, pageCount - 1)
    val start = (page * pageSize).coerceAtMost(focusedRows.size)
    val end = (start + pageSize).coerceAtMost(focusedRows.size)
    return focusedRows.subList(start, end)
}

private fun extractHunks(rows: List<ResolvedFileChangeRow>): List<DiffHunk> {
    val hunks = mutableListOf<DiffHunk>()
    var index = 0
    while (index < rows.size) {
        val kind = rows[index].line?.kind
        if (kind == null || kind == LineChangeKind.UNCHANGED) {
            index++
            continue
        }
        val start = index
        var hasAdded = false
        var hasDeleted = false
        var firstAddedIndex: Int? = null
        var firstDeletedIndex: Int? = null
        while (index < rows.size) {
            val currentKind = rows[index].line?.kind
            if (currentKind == null || currentKind == LineChangeKind.UNCHANGED) break
            if (currentKind == LineChangeKind.ADDED) {
                hasAdded = true
                if (firstAddedIndex == null) firstAddedIndex = index
            }
            if (currentKind == LineChangeKind.DELETED) {
                hasDeleted = true
                if (firstDeletedIndex == null) firstDeletedIndex = index
            }
            index++
        }
        hunks += DiffHunk(
            startIndex = start,
            endIndex = index - 1,
            hasAdded = hasAdded,
            hasDeleted = hasDeleted,
            firstAddedIndex = firstAddedIndex,
            firstDeletedIndex = firstDeletedIndex,
        )
    }
    return hunks
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
    val added = rows.count { it.line?.kind == LineChangeKind.ADDED }
    val deleted = rows.count { it.line?.kind == LineChangeKind.DELETED }
    val pending = rows.count { it.approvalState == RowApprovalState.PENDING }
    val modified = countModifiedLines(rows.mapNotNull { it.line?.kind })
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
    val kind = row.line?.kind ?: return null
    if (kind == LineChangeKind.DELETED) return styles.deletedBackground
    if (hasApprovalConfig) {
        return if (row.approvalState == RowApprovalState.PENDING) styles.pendingBackground else null
    }
    return if (kind == LineChangeKind.ADDED) styles.addedBackground else null
}

internal fun normalizeRowFill(style: TextStyle?): TextStyle? {
    val current = style ?: return null
    return when {
        current.bgColor != null -> current
        current.color != null -> current.bg
        else -> current
    }
}

internal fun mergeStyles(base: TextStyle?, extra: TextStyle?): TextStyle? {
    return when {
        base == null -> extra
        extra == null -> base
        else -> base + extra
    }
}

internal fun resolveMarkerStyle(
    line: FileChangeLine,
    styles: FileChangePreviewStyles,
): TextStyle? {
    return when (line.kind) {
        LineChangeKind.ADDED -> rgb("#699862") + TextStyle(bold = true)
        LineChangeKind.DELETED -> rgb("#6E3D37") + TextStyle(bold = true)
        LineChangeKind.UNCHANGED -> styles.gutterMarkerStyle
    }
}

internal fun formatDataRow(
    line: FileChangeLine,
    lineDigits: Int,
    @Suppress("UNUSED_PARAMETER") styles: FileChangePreviewStyles,
): String {
    val visibleLineNumber = line.newLineNumber ?: line.oldLineNumber
    val numberPart = visibleLineNumber?.toString()?.padStart(lineDigits) ?: " ".repeat(lineDigits)
    val marker = when (line.kind) {
        LineChangeKind.ADDED -> "+"
        LineChangeKind.DELETED -> "-"
        LineChangeKind.UNCHANGED -> " "
    }
    return "$numberPart $marker ${line.text}"
}

internal fun formatStats(stats: FileChangeStats): String =
    "lines=${stats.totalLines}  +${stats.addedLines}  -${stats.deletedLines}  ~${stats.modifiedLines}  pending=${stats.pendingLines}"

internal fun applyStyle(text: String, style: TextStyle?): String = style?.invoke(text) ?: text
