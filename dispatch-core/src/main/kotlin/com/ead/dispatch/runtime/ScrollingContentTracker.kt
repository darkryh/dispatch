package com.ead.dispatch.runtime

/**
 * Tracks which scrolling lines have already been appended to the terminal.
 *
 * Dispatch's scrollback rendering assumes the scrolling region is append-only: when scrolling content
 * changes, new lines are added at the end. This tracker returns only the lines that haven't been
 * appended yet.
 */
internal class ScrollingContentTracker {
    private var previousContentHash: Int = Int.MIN_VALUE
    private var scrolledLineCount: Int = 0

    val committedLineCount: Int
        get() = scrolledLineCount

    fun consume(scrollingLines: List<String>): ScrollUpdate {
        if (scrollingLines.isEmpty()) {
            return ScrollUpdate(emptyList(), reset = false)
        }

        val contentHash = scrollingLines.hashCode()
        if (contentHash == previousContentHash) {
            return ScrollUpdate(emptyList(), reset = false)
        }

        val shouldReset = !isAppend(scrollingLines)
        val linesToAppend = if (shouldReset) {
            scrollingLines
        } else {
            scrollingLines.drop(scrolledLineCount)
        }

        scrolledLineCount = scrollingLines.size
        previousContentHash = contentHash

        return ScrollUpdate(linesToAppend, reset = shouldReset)
    }

    fun reset() {
        previousContentHash = Int.MIN_VALUE
        scrolledLineCount = 0
    }

    fun sync(scrollingLines: List<String>) {
        if (scrollingLines.isEmpty()) {
            reset()
            return
        }
        previousContentHash = scrollingLines.hashCode()
        scrolledLineCount = scrollingLines.size
    }

    private fun isAppend(scrollingLines: List<String>): Boolean {
        if (scrolledLineCount == 0) return true
        if (scrollingLines.size < scrolledLineCount) return false
        val prefixHash = scrollingLines.take(scrolledLineCount).hashCode()
        return prefixHash == previousContentHash
    }
}

internal data class ScrollUpdate(
    val lines: List<String>,
    val reset: Boolean,
)
