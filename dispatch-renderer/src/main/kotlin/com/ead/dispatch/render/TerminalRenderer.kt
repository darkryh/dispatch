package com.ead.dispatch.render

import com.github.ajalt.mordant.terminal.Terminal
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Renders terminal output with append-only scrolling content and an in-place active area.
 */
class TerminalRenderer(
    private val terminal: Terminal,
) {
    /**
     * Lock for thread-safe rendering.
     */
    private val renderLock = ReentrantLock()

    /**
     * Current terminal size - detected from Mordant Terminal.
     */
    val terminalWidth: Int
        get() = terminal.size.width.coerceAtLeast(40)

    val terminalHeight: Int
        get() = terminal.size.height.coerceAtLeast(10)

    // ========== Active Area Management ==========

    /**
     * Current active area lines (for in-place updates).
     */
    private var activeAreaLines: List<String> = emptyList()

    /**
     * Last known visible line count of the composed viewport.
     */
    private var visibleContentLineCount: Int = 0

    /**
     * Whether the active area has been initially rendered.
     */
    private var activeAreaInitialized = false

    /**
     * Append scrolling content to the terminal.
     * This content flows naturally with terminal scrollback.
     *
     * Uses atomic rendering to prevent flickering: clears active area,
     * prints scrolling content, and restores active area in one buffered operation.
     *
     * @param lines Lines to append (will scroll naturally).
     */
    fun appendScrollingContent(lines: List<String>) {
        if (lines.isEmpty()) return

        renderLock.withLock {
            val buffer = StringBuilder()
            clearActiveAreaInto(buffer)
            appendLines(buffer, lines)
            restoreActiveAreaInto(buffer)
            flushBuffer(buffer, "append_scrolling")
        }
    }

    /**
     * Rewrite the visible viewport in-place without appending to terminal scrollback.
     *
     * Use this for non-append content changes (mode/screen rewrites).
     */
    fun rewriteViewport(
        scrollingLines: List<String>,
        activeLines: List<String>,
        clearScrollback: Boolean = true,
    ) {
        renderLock.withLock {
            val buffer = StringBuilder()
            if (clearScrollback) {
                // Keep the visible viewport intact. Rows are replaced below before obsolete
                // trailing rows are erased, so the terminal never observes a blank frame.
                buffer.append(AnsiCodes.CLEAR_SCROLLBACK)
            }
            buffer.append(AnsiCodes.CURSOR_HOME)
            val contentLineCount = scrollingLines.size + activeLines.size
            appendViewportLinesWithoutTrailingNewline(buffer, scrollingLines, activeLines)
            clearViewportRowsAfter(buffer, contentLineCount)
            if (contentLineCount > 0) {
                buffer.append(AnsiCodes.moveTo(contentLineCount.coerceAtMost(terminalHeight), 1))
            }
            flushBuffer(buffer, "rewrite_viewport")

            activeAreaLines = activeLines
            activeAreaInitialized = activeLines.isNotEmpty()
        }
    }

    private fun clearViewportRowsAfter(
        buffer: StringBuilder,
        contentLineCount: Int,
    ) {
        val firstBlankRow = (contentLineCount + 1).coerceAtLeast(1)
        for (row in firstBlankRow..terminalHeight.coerceAtLeast(1)) {
            AnsiCodes.appendMoveTo(buffer, row, 1)
            buffer.append(AnsiCodes.CLEAR_LINE)
        }
    }

    /**
     * Update the active area at the bottom of the terminal.
     * This area updates in-place without scrolling.
     *
     * Uses atomic rendering to prevent flickering: builds all ANSI commands
     * in a buffer first, then flushes once.
     *
     * @param lines Lines for the active area (input field, status bar, etc.).
     */
    fun updateActiveArea(lines: List<String>) {
        renderLock.withLock {
            // We assume the layout engine has already constrained the lines to the terminal width.
            // Naive truncation with take() breaks ANSI escape codes.
            val displayedLines = lines

            val hasAnyContentChange = displayedLines != activeAreaLines
            // Skip if nothing changed
            if (!hasAnyContentChange && activeAreaInitialized) return

            val oldLineCount = if (activeAreaInitialized) activeAreaLines.size else 0
            val newLineCount = displayedLines.size
            val maxLineCount = maxOf(oldLineCount, newLineCount)
            // If any line changed, repaint the whole active area. This keeps sibling lines stable
            // when external input methods momentarily desynchronize terminal output.
            val forceRedraw = oldLineCount != newLineCount || hasAnyContentChange

            // Build entire update in a buffer to send atomically (prevents flickering)
            val buffer = StringBuilder()
            moveToActiveAreaTop(buffer, oldLineCount)
            appendActiveAreaUpdates(
                buffer = buffer,
                newLines = displayedLines,
                maxLineCount = maxLineCount,
                forceRedraw = forceRedraw,
            )
            moveCursorAfterShrink(buffer, oldLineCount, newLineCount)
            flushBuffer(buffer, "update_active_area")

            activeAreaLines = displayedLines
            activeAreaInitialized = true
        }
    }

    /**
     * Clear the active area completely.
     * Uses atomic rendering to prevent flickering.
     */
    fun clearActiveArea() {
        renderLock.withLock {
            if (activeAreaInitialized && activeAreaLines.isNotEmpty()) {
                val buffer = StringBuilder()
                clearActiveAreaInto(buffer)
                flushBuffer(buffer, "clear_active_area")
            }
            activeAreaLines = emptyList()
            activeAreaInitialized = false
        }
    }

    /**
     * Prepare terminal state for returning control to the shell prompt.
     *
     * This clears the active area and positions the cursor on a fresh line so
     * the shell prompt doesn't appear in the middle of the UI frame.
     */
    fun handoffToShellPrompt() {
        renderLock.withLock {
            val buffer = StringBuilder()
            val trailingBlankLines = activeAreaLines.trailingBlankLineCount()
            val targetRow =
                (visibleContentLineCount - trailingBlankLines)
                    .coerceAtLeast(1)
                    .coerceAtMost(terminalHeight)
            buffer.append(AnsiCodes.moveTo(targetRow, 1))
            buffer.append(AnsiCodes.CLEAR_LINE)
            buffer.append("\n")
            flushBuffer(buffer, "shell_handoff")

            activeAreaLines = emptyList()
            activeAreaInitialized = false
            visibleContentLineCount = 0
        }
    }

    private fun clearActiveAreaInto(buffer: StringBuilder) {
        if (!activeAreaInitialized || activeAreaLines.isEmpty()) return
        val lastIndex = activeAreaLines.lastIndex
        for (index in 0..lastIndex) {
            buffer.append("\r")
            buffer.append(AnsiCodes.CLEAR_LINE)
            if (index < lastIndex) {
                buffer.append(AnsiCodes.moveUp(1))
            }
        }
    }

    private fun appendLines(
        buffer: StringBuilder,
        lines: List<String>,
    ) {
        for (line in lines) {
            buffer.append(line)
            buffer.append("\n")
        }
    }

    private fun appendViewportLinesWithoutTrailingNewline(
        buffer: StringBuilder,
        scrollingLines: List<String>,
        activeLines: List<String>,
    ) {
        val total = scrollingLines.size + activeLines.size
        if (total == 0) return
        var emitted = 0
        for (line in scrollingLines) {
            appendViewportLine(buffer, line, isLast = emitted == total - 1)
            emitted++
        }
        for (line in activeLines) {
            appendViewportLine(buffer, line, isLast = emitted == total - 1)
            emitted++
        }
    }

    private fun appendViewportLine(
        buffer: StringBuilder,
        line: String,
        isLast: Boolean,
    ) {
        buffer.append("\r")
        buffer.append(AnsiCodes.CLEAR_LINE)
        buffer.append(line)
        if (!isLast) {
            buffer.append("\n")
        }
    }

    private fun restoreActiveAreaInto(buffer: StringBuilder) {
        if (!activeAreaInitialized || activeAreaLines.isEmpty()) return
        for ((index, line) in activeAreaLines.withIndex()) {
            buffer.append("\r")
            buffer.append(AnsiCodes.CLEAR_LINE)
            buffer.append(line)
            if (index < activeAreaLines.lastIndex) {
                buffer.append("\n")
            }
        }
    }

    private fun moveToActiveAreaTop(
        buffer: StringBuilder,
        oldLineCount: Int,
    ) {
        if (oldLineCount > 1) {
            buffer.append(AnsiCodes.moveUp(oldLineCount - 1))
        }
    }

    private fun appendActiveAreaUpdates(
        buffer: StringBuilder,
        newLines: List<String>,
        maxLineCount: Int,
        forceRedraw: Boolean,
    ) {
        for (index in 0 until maxLineCount) {
            appendSingleActiveAreaLine(
                buffer = buffer,
                index = index,
                maxLineCount = maxLineCount,
                newLines = newLines,
                forceRedraw = forceRedraw,
            )
        }
    }

    private fun appendSingleActiveAreaLine(
        buffer: StringBuilder,
        index: Int,
        maxLineCount: Int,
        newLines: List<String>,
        forceRedraw: Boolean,
    ) {
        val oldLine = activeAreaLines.getOrNull(index)
        val newLine = newLines.getOrNull(index)

        buffer.append("\r")
        when {
            newLine == null -> {
                buffer.append(AnsiCodes.CLEAR_LINE)
            }
            forceRedraw || newLine != oldLine -> {
                buffer.append(AnsiCodes.CLEAR_LINE)
                buffer.append(newLine)
            }
            else -> {
                Unit
            }
        }

        if (index < maxLineCount - 1) {
            buffer.append("\n")
        }
    }

    private fun moveCursorAfterShrink(
        buffer: StringBuilder,
        oldLineCount: Int,
        newLineCount: Int,
    ) {
        if (oldLineCount > newLineCount && newLineCount > 0) {
            buffer.append(AnsiCodes.moveUp(oldLineCount - newLineCount))
        }
    }

    private fun flushBuffer(
        buffer: StringBuilder,
        operation: String,
    ) {
        val startedAt = System.nanoTime()
        OutputCapture.suppress {
            terminal.rawPrint(buffer)
            System.out.flush()
        }
        RenderDiagnostics.record(
            event = "terminal_write",
            fields =
                mapOf(
                    "operation" to operation,
                    "bytes" to buffer.toString().toByteArray(Charsets.UTF_8).size,
                    "chars" to buffer.length,
                    "clearScreen" to buffer.contains(AnsiCodes.CLEAR_SCREEN),
                    "clearScrollback" to buffer.contains(AnsiCodes.CLEAR_SCROLLBACK),
                    "clearLines" to buffer.countOccurrences(AnsiCodes.CLEAR_LINE),
                    "durationNanos" to System.nanoTime() - startedAt,
                ),
        )
    }

    /**
     * Show the cursor.
     */
    fun showCursor() {
        renderLock.withLock {
            OutputCapture.suppress {
                terminal.cursor.show()
                System.out.flush()
            }
            RenderDiagnostics.record("cursor", mapOf("visible" to true))
        }
    }

    /**
     * Hide the cursor.
     */
    fun hideCursor() {
        renderLock.withLock {
            OutputCapture.suppress {
                terminal.cursor.hide()
                System.out.flush()
            }
            RenderDiagnostics.record("cursor", mapOf("visible" to false))
        }
    }

    /**
     * Move cursor to a specific position.
     *
     * @param x Column (0-indexed).
     * @param y Row (0-indexed).
     */
    fun moveCursor(
        x: Int,
        y: Int,
    ) {
        renderLock.withLock {
            OutputCapture.suppress {
                terminal.cursor.move {
                    setPosition(x, y)
                }
                System.out.flush()
            }
        }
    }

    /**
     * Clear the screen completely.
     */
    fun clearScreen(clearScrollback: Boolean = false) {
        renderLock.withLock {
            OutputCapture.suppress {
                if (clearScrollback) {
                    terminal.rawPrint(AnsiCodes.CLEAR_SCROLLBACK)
                }
                terminal.cursor.move {
                    clearScreen()
                    setPosition(0, 0)
                }
                System.out.flush()
            }
            activeAreaLines = emptyList()
            activeAreaInitialized = false
            RenderDiagnostics.record(
                "terminal_write",
                mapOf(
                    "operation" to "clear_screen",
                    "clearScreen" to true,
                    "clearScrollback" to clearScrollback,
                ),
            )
        }
    }

    /**
     * Ring the terminal bell.
     */
    fun bell() {
        OutputCapture.suppress {
            terminal.rawPrint("\u0007")
            System.out.flush()
        }
    }

    /**
     * Track how many lines are currently visible in the rendered viewport.
     *
     * Dispatch runtime updates this every frame so shell handoff can restore the
     * prompt close to visible content instead of jumping to terminal bottom.
     */
    fun markVisibleContentHeight(lineCount: Int) {
        renderLock.withLock {
            visibleContentLineCount = lineCount.coerceAtLeast(0)
        }
    }
}

private fun CharSequence.countOccurrences(token: String): Int {
    var count = 0
    var index = 0
    while (index <= length - token.length) {
        val found = indexOf(token, index)
        if (found < 0) break
        count += 1
        index = found + token.length
    }
    return count
}

private fun List<String>.trailingBlankLineCount(): Int {
    var count = 0
    for (index in lastIndex downTo 0) {
        if (this[index].isDisplayBlank()) {
            count += 1
            continue
        }
        break
    }
    return count
}

/**
 * A line is "display blank" if every visible character (ignoring ANSI escape runs) is whitespace.
 *
 * Implemented as a single linear scan that skips CSI/OSC/single-char escape sequences, replacing
 * the previous two full-string regex `.replace` passes per line.
 */
private fun String.isDisplayBlank(): Boolean {
    var index = 0
    while (index < length) {
        val c = this[index]
        if (c == '\u001B') {
            val skip = ansiEscapeLength(this, index)
            if (skip > 0) {
                index += skip
                continue
            }
        }
        if (!c.isWhitespace()) return false
        index++
    }
    return true
}

private fun ansiEscapeLength(text: String, start: Int): Int {
    if (start + 1 >= text.length) return 0
    return when (text[start + 1]) {
        '[' -> {
            // CSI: ESC [ ... <final byte 0x40..0x7E>
            var i = start + 2
            while (i < text.length) {
                if (text[i] in '@'..'~') return i - start + 1
                i++
            }
            0
        }
        ']' -> {
            // OSC: ESC ] ... BEL or ESC backslash
            var i = start + 2
            while (i < text.length) {
                if (text[i] == '\u0007') return i - start + 1
                if (text[i] == '\u001B' && i + 1 < text.length && text[i + 1] == '\\') return i - start + 2
                i++
            }
            0
        }
        else -> 2
    }
}

/**
 * ANSI escape codes for terminal control.
 */
object AnsiCodes {
    // Cursor control
    const val CURSOR_HOME = "\u001B[H"
    const val CURSOR_HIDE = "\u001B[?25l"
    const val CURSOR_SHOW = "\u001B[?25h"
    const val SAVE_CURSOR = "\u001B[s"
    const val RESTORE_CURSOR = "\u001B[u"

    // Screen control
    const val CLEAR_SCREEN = "\u001B[2J"
    const val CLEAR_SCROLLBACK = "\u001B[3J"
    const val CLEAR_LINE = "\u001B[2K"
    const val CLEAR_TO_END = "\u001B[J"

    // Cached single-row moves: callers always pass 1, so avoid rebuilding these per frame.
    private const val MOVE_UP_1 = "\u001B[1A"
    private const val MOVE_DOWN_1 = "\u001B[1B"

    // Cursor movement
    fun moveTo(
        row: Int,
        col: Int,
    ) = "\u001B[$row;${col}H"

    /**
     * Append a cursor-move-to sequence directly into [buffer] instead of allocating a String per
     * row (used on full-screen viewport clears that move to every line).
     */
    fun appendMoveTo(
        buffer: StringBuilder,
        row: Int,
        col: Int,
    ) {
        buffer.append("\u001B[").append(row).append(';').append(col).append('H')
    }

    fun moveUp(n: Int = 1) = if (n == 1) MOVE_UP_1 else "\u001B[${n}A"

    fun moveDown(n: Int = 1) = if (n == 1) MOVE_DOWN_1 else "\u001B[${n}B"

    fun moveRight(n: Int = 1) = "\u001B[${n}C"

    fun moveLeft(n: Int = 1) = "\u001B[${n}D"

    // Scrolling
    fun scrollUp(n: Int = 1) = "\u001B[${n}S"

    fun scrollDown(n: Int = 1) = "\u001B[${n}T"
}
