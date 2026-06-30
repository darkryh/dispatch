package io.github.darkryh.dispatch.widget

/**
 * Internal line-diff engine shared by [FileDiff] / [rememberFileDiff].
 *
 * This is a verbatim relocation of the diff math that used to live in the sample's
 * `FileChangePreview`: the LCS table, line splitting, hunk extraction, focus windowing,
 * paging and stat counting. No approval/expiry awareness lives here — pending coloring
 * is applied at render time via a `pending` set of after-text line numbers.
 */

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

/**
 * Approval-free row used by the engine. A row is either a diff [line] or a collapsed
 * placeholder ([isCollapsed] == true, [line] == null).
 */
internal data class DiffRowInternal(
    val line: FileChangeLine?,
    val isCollapsed: Boolean = false,
)

internal data class DiffHunk(
    val startIndex: Int,
    val endIndex: Int,
    val hasAdded: Boolean,
    val hasDeleted: Boolean,
    val firstAddedIndex: Int?,
    val firstDeletedIndex: Int?,
)

internal fun diffLines(
    beforeText: String,
    afterText: String,
): List<FileChangeLine> {
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
            rows +=
                FileChangeLine(
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
            rows +=
                FileChangeLine(
                    kind = LineChangeKind.DELETED,
                    oldLineNumber = oldLine++,
                    newLineNumber = null,
                    text = before[i],
                )
            i++
        } else {
            rows +=
                FileChangeLine(
                    kind = LineChangeKind.ADDED,
                    oldLineNumber = null,
                    newLineNumber = newLine++,
                    text = after[j],
                )
            j++
        }
    }

    while (i < before.size) {
        rows +=
            FileChangeLine(
                kind = LineChangeKind.DELETED,
                oldLineNumber = oldLine++,
                newLineNumber = null,
                text = before[i],
            )
        i++
    }

    while (j < after.size) {
        rows +=
            FileChangeLine(
                kind = LineChangeKind.ADDED,
                oldLineNumber = null,
                newLineNumber = newLine++,
                text = after[j],
            )
        j++
    }

    return rows
}

internal fun lcsTable(
    before: List<String>,
    after: List<String>,
): Array<IntArray> {
    val rows = before.size
    val cols = after.size
    val table = Array(rows + 1) { IntArray(cols + 1) }

    for (i in rows - 1 downTo 0) {
        for (j in cols - 1 downTo 0) {
            table[i][j] =
                if (before[i] == after[j]) {
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

internal fun extractHunks(rows: List<DiffRowInternal>): List<DiffHunk> {
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
        hunks +=
            DiffHunk(
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

/**
 * Focus + context windowing. Mirrors the old `resolveFocusedRows`, parameterized on
 * [DiffFocus] instead of the removed `ChangeFocusMode`:
 *  - [DiffFocus.Full] keeps every row.
 *  - [DiffFocus.Additions] / [DiffFocus.Deletions] reproduce the old ADDED_FIRST / DELETED_ONLY behavior.
 *  - [DiffFocus.Changes] targets all changed hunks regardless of kind, anchored at each hunk start.
 */
internal fun resolveFocusedRows(
    allRows: List<DiffRowInternal>,
    focus: DiffFocus,
    contextLines: Int,
    showCollapsedUnchanged: Boolean = true,
): List<DiffRowInternal> {
    if (focus == DiffFocus.Full) return allRows
    val hunks = extractHunks(allRows)
    if (hunks.isEmpty()) return allRows

    val targetHunks =
        when (focus) {
            DiffFocus.Additions -> {
                val preferred = hunks.filter { it.hasAdded }
                if (preferred.isNotEmpty()) preferred else hunks
            }
            DiffFocus.Deletions -> {
                val preferred = hunks.filter { it.hasDeleted && !it.hasAdded }
                if (preferred.isNotEmpty()) preferred else hunks
            }
            DiffFocus.Changes -> hunks
            DiffFocus.Full -> hunks
        }

    val mergedSegments = mutableListOf<IntRange>()
    targetHunks.sortedBy { it.startIndex }.forEach { hunk ->
        val anchorIndex =
            when (focus) {
                DiffFocus.Additions -> hunk.firstAddedIndex ?: hunk.startIndex
                DiffFocus.Deletions -> hunk.firstDeletedIndex ?: hunk.startIndex
                DiffFocus.Changes -> hunk.startIndex
                DiffFocus.Full -> hunk.startIndex
            }
        val start = (anchorIndex - contextLines).coerceAtLeast(0)
        val end = (hunk.endIndex + contextLines).coerceAtMost(allRows.lastIndex)
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
    if (!showCollapsedUnchanged) {
        return mergedSegments.flatMap { allRows.subList(it.first, it.last + 1) }
    }

    val result = mutableListOf<DiffRowInternal>()
    mergedSegments.forEachIndexed { index, segment ->
        if (index == 0) {
            if (segment.first > 0) {
                result += DiffRowInternal(line = null, isCollapsed = true)
            }
        } else {
            result += DiffRowInternal(line = null, isCollapsed = true)
        }
        result += allRows.subList(segment.first, segment.last + 1)
    }
    if (mergedSegments.last().last < allRows.lastIndex) {
        result += DiffRowInternal(line = null, isCollapsed = true)
    }
    return result
}

internal fun calculateStats(
    rows: List<DiffRowInternal>,
    afterText: String,
): DiffStats {
    val added = rows.count { it.line?.kind == LineChangeKind.ADDED }
    val deleted = rows.count { it.line?.kind == LineChangeKind.DELETED }
    val modified = countModifiedLines(rows.mapNotNull { it.line?.kind })
    return DiffStats(
        totalLines = splitFileLines(afterText).size,
        addedLines = added,
        deletedLines = deleted,
        modifiedLines = modified,
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
