package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height

/**
 * Which part of a diff [FileDiff] / [rememberFileDiff] focuses on.
 *
 * Maps from the old sample `ChangeFocusMode`:
 *  - [Additions] == old `ADDED_FIRST` (prefers hunks that add lines, anchored at the first added line).
 *  - [Deletions] == old `DELETED_ONLY` (prefers pure-deletion hunks, falling back to all hunks).
 *  - [Full] == old `FULL` (no focusing; every row shown).
 *  - [Changes] is the new default: all changed hunks regardless of kind, each anchored at its start.
 */
enum class DiffFocus {
    Changes,
    Additions,
    Deletions,
    Full,
}

/** Summary metrics for a diff. Approval/pending counts intentionally excluded — they are app policy. */
data class DiffStats(
    val totalLines: Int,
    val addedLines: Int,
    val deletedLines: Int,
    val modifiedLines: Int,
)

/**
 * Intent-named color knobs for [FileDiff]. Defaults reproduce the old `FileChangePreviewStyles`
 * values so rendered output is unchanged.
 */
data class DiffColors(
    val added: TextStyle = rgb("#1E4D31"),
    val deleted: TextStyle = rgb("#5A2323"),
    val gutter: TextStyle = rgb("#6E7681"),
    val header: TextStyle = rgb("#E6EAF0") + TextStyle(bold = true),
    val stats: TextStyle = rgb("#8A95A5"),
    val content: TextStyle = rgb("#E6EAF0"),
) {
    companion object {
        val Default = DiffColors()
    }
}

/**
 * One rendered diff row. Either a collapsed placeholder ([isCollapsed]) or a content line with
 * a gutter marker (`'+'` added, `'-'` deleted, `' '` unchanged).
 */
data class DiffRow internal constructor(
    val isCollapsed: Boolean = false,
    val oldLineNumber: Int? = null,
    val newLineNumber: Int? = null,
    val marker: Char = ' ',
    val content: String = "",
)

/** Result of running the diff engine — stats plus focused, ready-to-render rows. */
class FileDiffResult internal constructor(
    val stats: DiffStats,
    val rows: List<DiffRow>,
) {
    /**
     * Slice [rows] into a page. Folds in the old `computeFileChangePageInfo` + `resolveDisplayRows`:
     * [index] is clamped into range and the page count is reported back.
     */
    fun page(
        index: Int,
        size: Int,
    ): FileDiffPage {
        require(size > 0) { "size must be > 0" }
        val pageCount = ((rows.size + size - 1) / size).coerceAtLeast(1)
        val pageIndex = index.coerceIn(0, pageCount - 1)
        val start = (pageIndex * size).coerceAtMost(rows.size)
        val end = (start + size).coerceAtMost(rows.size)
        return FileDiffPage(
            rows = rows.subList(start, end),
            pageCount = pageCount,
            pageIndex = pageIndex,
        )
    }
}

/** A single page of diff rows produced by [FileDiffResult.page]. */
data class FileDiffPage(
    val rows: List<DiffRow>,
    val pageCount: Int,
    val pageIndex: Int,
)

/**
 * Run the diff engine and remember the result keyed by ([before], [after], [focus], [contextLines]).
 * Use this when you need the raw rows/stats (e.g. to build paged or custom chrome around the diff).
 */
@Composable
fun rememberFileDiff(
    before: String,
    after: String,
    focus: DiffFocus = DiffFocus.Changes,
    contextLines: Int = 3,
): FileDiffResult =
    remember(before, after, focus, contextLines) {
        computeFileDiff(before, after, focus, contextLines)
    }

internal fun computeFileDiff(
    before: String,
    after: String,
    focus: DiffFocus,
    contextLines: Int,
): FileDiffResult {
    require(contextLines >= 0) { "contextLines must be >= 0" }
    val allRows = diffLines(before, after).map { DiffRowInternal(it) }
    val focused = resolveFocusedRows(allRows, focus, contextLines, showCollapsedUnchanged = true)
    val stats = calculateStats(allRows, after)
    return FileDiffResult(stats = stats, rows = focused.map { it.toDiffRow() })
}

private fun DiffRowInternal.toDiffRow(): DiffRow {
    if (isCollapsed || line == null) return DiffRow(isCollapsed = true)
    return DiffRow(
        isCollapsed = false,
        oldLineNumber = line.oldLineNumber,
        newLineNumber = line.newLineNumber,
        marker =
            when (line.kind) {
                LineChangeKind.ADDED -> '+'
                LineChangeKind.DELETED -> '-'
                LineChangeKind.UNCHANGED -> ' '
            },
        content = line.text,
    )
}

/**
 * Render a line-based file diff with a diff-style gutter (line number + marker + content).
 *
 * Scroll position, hunk math, paging, gutter widths and collapse markers are all handled
 * internally — the caller never manages a page index. Approval/expiry is app policy: pass the
 * already-resolved set of pending after-text line numbers via [pending].
 *
 * @param before original text.
 * @param after new text.
 * @param path optional file path shown in the header.
 * @param focus which part of the diff to focus on.
 * @param contextLines unchanged lines of context kept around each focused hunk.
 * @param showHeader show the "File: …" header line.
 * @param showStats show the summary stats line.
 * @param colors color knobs (defaults match the legacy widget).
 * @param maxVisibleRows cap the visible body height (scroll handled internally) when non-null.
 * @param pending after-text line numbers currently pending approval.
 * @param pendingColor background applied to pending lines.
 */
@Composable
fun FileDiff(
    before: String,
    after: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    focus: DiffFocus = DiffFocus.Changes,
    contextLines: Int = 3,
    showHeader: Boolean = true,
    showStats: Boolean = true,
    colors: DiffColors = DiffColors(),
    maxVisibleRows: Int? = null,
    pending: Set<Int> = emptySet(),
    pendingColor: TextStyle = DEFAULT_PENDING_COLOR,
) {
    require(maxVisibleRows == null || maxVisibleRows > 0) { "maxVisibleRows must be > 0 when specified" }

    val result = rememberFileDiff(before, after, focus, contextLines)
    val scrollState = rememberScrollState()
    val rows = result.rows
    val oldDigits =
        rows
            .mapNotNull { it.oldLineNumber }
            .maxOfOrNull { it.toString().length }
            ?.coerceAtLeast(1)
            ?: 1
    val newDigits =
        rows
            .mapNotNull { it.newLineNumber }
            .maxOfOrNull { it.toString().length }
            ?.coerceAtLeast(1)
            ?: 1
    val lineDigits = maxOf(oldDigits, newDigits)

    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeader) {
            val fileId = path?.takeIf { it.isNotBlank() } ?: "(unnamed)"
            Text(text = "File: $fileId", style = colors.header)
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), char = DIVIDER_CHAR)
        }

        if (showStats) {
            Text(text = formatStats(result.stats), style = colors.stats)
        }

        if (showHeader || showStats) {
            HorizontalDivider(modifier = Modifier.fillMaxWidth(), char = DIVIDER_CHAR)
        }

        val bodyModifier =
            if (maxVisibleRows != null) {
                Modifier.fillMaxWidth().height(maxVisibleRows)
            } else {
                Modifier.fillMaxWidth()
            }

        ScrollableList(
            items = rows,
            modifier = bodyModifier,
            scrollState = scrollState,
        ) { row ->
            DiffPreviewRow(
                row = row,
                lineDigits = lineDigits,
                colors = colors,
                pending = pending,
                pendingColor = pendingColor,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun DiffPreviewRow(
    row: DiffRow,
    lineDigits: Int,
    colors: DiffColors,
    pending: Set<Int>,
    pendingColor: TextStyle,
    modifier: Modifier = Modifier,
) {
    if (row.isCollapsed) {
        Row(modifier = modifier) {
            Text(
                text = applyStyle("…", COLLAPSE_STYLE),
                modifier = Modifier.fillMaxWidth(),
                style = null,
            )
        }
        return
    }

    val visibleLineNumber = row.newLineNumber ?: row.oldLineNumber
    val numberPart = visibleLineNumber?.toString()?.padStart(lineDigits) ?: " ".repeat(lineDigits)
    val background = resolveRowBackground(row, pending, colors, pendingColor)
    val contentStyle = mergeStyles(colors.content, normalizeRowFill(background))

    Row(modifier = modifier) {
        Text(text = numberPart, style = colors.gutter)
        Text(text = " ", style = null)
        Text(text = row.marker.toString(), style = resolveMarkerStyle(row))
        Text(text = " ", style = null)
        Text(
            text = row.content,
            modifier = Modifier.fillMaxWidth(),
            style = contentStyle,
        )
    }
}

/** Background precedence: deleted > pending > added > none (unchanged). */
internal fun resolveRowBackground(
    row: DiffRow,
    pending: Set<Int>,
    colors: DiffColors,
    pendingColor: TextStyle,
): TextStyle? =
    when {
        row.marker == '-' -> colors.deleted
        row.newLineNumber != null && row.newLineNumber in pending -> pendingColor
        row.marker == '+' -> colors.added
        else -> null
    }

internal fun resolveMarkerStyle(row: DiffRow): TextStyle? =
    when (row.marker) {
        '+' -> rgb("#699862") + TextStyle(bold = true)
        '-' -> rgb("#6E3D37") + TextStyle(bold = true)
        else -> GUTTER_MARKER_STYLE
    }

internal fun normalizeRowFill(style: TextStyle?): TextStyle? {
    val current = style ?: return null
    return when {
        current.bgColor != null -> current
        current.color != null -> current.bg
        else -> current
    }
}

internal fun mergeStyles(
    base: TextStyle?,
    extra: TextStyle?,
): TextStyle? =
    when {
        base == null -> extra
        extra == null -> base
        else -> base + extra
    }

internal fun formatStats(stats: DiffStats): String =
    "lines=${stats.totalLines}  +${stats.addedLines}  -${stats.deletedLines}  ~${stats.modifiedLines}"

internal fun applyStyle(
    text: String,
    style: TextStyle?,
): String = style?.invoke(text) ?: text

private const val DIVIDER_CHAR: Char = '─'
private val DEFAULT_PENDING_COLOR: TextStyle = rgb("#1F3F6B")
private val COLLAPSE_STYLE: TextStyle = rgb("#A7B2BF")
private val GUTTER_MARKER_STYLE: TextStyle = rgb("#4E5561")
