package com.ead.dispatch.runtime

/**
 * Tracks which scrolling lines have already been appended to the terminal.
 *
 * Dispatch supports two render paths:
 * - Append-only updates for true history growth.
 * - Viewport rewrite updates when the scrolling region changes in-place.
 */
internal class ScrollingContentTracker {
    private val committedLines = mutableListOf<String>()

    val committedLineCount: Int
        get() = committedLines.size

    fun consume(scrollingLines: List<String>): ScrollUpdate {
        if (scrollingLines.isEmpty()) {
            if (committedLines.isEmpty()) return ScrollUpdate.none()
            committedLines.clear()
            return ScrollUpdate.rewrite(emptyList())
        }

        if (committedLines.isEmpty()) {
            committedLines.clear()
            committedLines.addAll(scrollingLines)
            return ScrollUpdate.append(scrollingLines)
        }

        if (startsWithCommitted(scrollingLines)) {
            val linesToAppend = scrollingLines.drop(committedLines.size)
            committedLines.clear()
            committedLines.addAll(scrollingLines)
            if (linesToAppend.isEmpty()) return ScrollUpdate.none()
            return ScrollUpdate.append(linesToAppend)
        }

        // Non-append rewrite: caller should repaint viewport without growing scrollback.
        committedLines.clear()
        committedLines.addAll(scrollingLines)
        return ScrollUpdate.rewrite(scrollingLines)
    }

    private fun startsWithCommitted(scrollingLines: List<String>): Boolean {
        if (scrollingLines.size < committedLines.size) return false
        for (index in committedLines.indices) {
            if (scrollingLines[index] != committedLines[index]) return false
        }
        return true
    }

    fun reset() {
        committedLines.clear()
    }

    fun sync(scrollingLines: List<String>) {
        if (scrollingLines.isEmpty()) {
            reset()
            return
        }
        committedLines.clear()
        committedLines.addAll(scrollingLines)
    }
}

internal data class ScrollUpdate(
    val lines: List<String>,
    val kind: ScrollUpdateKind,
) {

    companion object {
        fun none(): ScrollUpdate = ScrollUpdate(emptyList(), ScrollUpdateKind.NONE)
        fun append(lines: List<String>): ScrollUpdate = ScrollUpdate(lines, ScrollUpdateKind.APPEND)
        fun rewrite(lines: List<String>): ScrollUpdate = ScrollUpdate(lines, ScrollUpdateKind.REWRITE)
    }
}

internal enum class ScrollUpdateKind {
    NONE,
    APPEND,
    REWRITE,
}
