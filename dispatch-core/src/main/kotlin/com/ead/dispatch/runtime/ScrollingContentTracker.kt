package com.ead.dispatch.runtime

/**
 * Tracks which scrolling lines have already been appended to the terminal.
 *
 * Dispatch's scrollback rendering assumes the scrolling region is append-only: when scrolling content
 * changes, new lines are added at the end. This tracker returns only the lines that haven't been
 * appended yet.
 */
internal class ScrollingContentTracker {
    private val committedLines = mutableListOf<String>()

    val committedLineCount: Int
        get() = committedLines.size

    fun consume(scrollingLines: List<String>): ScrollUpdate {
        if (scrollingLines.isEmpty()) {
            return ScrollUpdate(emptyList(), reset = false)
        }

        if (scrollingLines == committedLines) {
            return ScrollUpdate(emptyList(), reset = false)
        }

        if (committedLines.isEmpty()) {
            committedLines.clear()
            committedLines.addAll(scrollingLines)
            return ScrollUpdate(scrollingLines, reset = false)
        }

        if (startsWithCommitted(scrollingLines)) {
            val linesToAppend = scrollingLines.drop(committedLines.size)
            committedLines.clear()
            committedLines.addAll(scrollingLines)
            return ScrollUpdate(linesToAppend, reset = false)
        }

        if (scrollingLines.size > committedLines.size) {
            // Prefix changed (non-append rewrite), but new tail lines still arrived.
            // Keep committed prefix immutable and append only the true new tail.
            val tailLines = scrollingLines.drop(committedLines.size)
            committedLines.addAll(tailLines)
            return ScrollUpdate(tailLines, reset = false)
        }

        if (scrollingLines.size == committedLines.size) {
            // Ignore in-place rewrites of already committed scrollback lines.
            // Re-rendering those would require full terminal reset and causes visible flicker.
            return ScrollUpdate(emptyList(), reset = false)
        }

        committedLines.clear()
        committedLines.addAll(scrollingLines)
        return ScrollUpdate(scrollingLines, reset = true)
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
    val reset: Boolean,
)
