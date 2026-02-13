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
     * Placeholder until cursor queries are implemented.
     */
    private val unavailableCursorPosition: Pair<Int, Int>? = null

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
            flushBuffer(buffer)
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
                buffer.append(AnsiCodes.CLEAR_SCROLLBACK)
            }
            buffer.append(AnsiCodes.CURSOR_HOME)
            buffer.append(AnsiCodes.CLEAR_SCREEN)
            buffer.append(AnsiCodes.CURSOR_HOME)

            val viewportLines = scrollingLines + activeLines
            appendViewportLinesWithoutTrailingNewline(buffer, viewportLines)
            flushBuffer(buffer)

            activeAreaLines = activeLines
            activeAreaInitialized = activeLines.isNotEmpty()
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
            flushBuffer(buffer)

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
                flushBuffer(buffer)
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
            flushBuffer(buffer)

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
        lines: List<String>,
    ) {
        if (lines.isEmpty()) return
        for (index in lines.indices) {
            buffer.append("\r")
            buffer.append(AnsiCodes.CLEAR_LINE)
            buffer.append(lines[index])
            if (index < lines.lastIndex) {
                buffer.append("\n")
            }
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

    private fun flushBuffer(buffer: StringBuilder) {
        OutputCapture.suppress {
            terminal.rawPrint(buffer)
            System.out.flush()
        }
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

    /**
     * Get current cursor position (if supported).
     *
     * Note: This requires terminal cooperation and may not work in all environments.
     */
    fun getCursorPosition(): Pair<Int, Int>? = unavailableCursorPosition
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

private val ansiCsiRegex = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
private val ansiOscRegex = Regex("\u001B\\][^\\u0007\\u001B]*(\\u0007|\u001B\\\\)")

private fun String.isDisplayBlank(): Boolean {
    val withoutOsc = replace(ansiOscRegex, "")
    val withoutAnsi = withoutOsc.replace(ansiCsiRegex, "")
    return withoutAnsi.isBlank()
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

    // Cursor movement
    fun moveTo(
        row: Int,
        col: Int,
    ) = "\u001B[$row;${col}H"

    fun moveUp(n: Int = 1) = "\u001B[${n}A"

    fun moveDown(n: Int = 1) = "\u001B[${n}B"

    fun moveRight(n: Int = 1) = "\u001B[${n}C"

    fun moveLeft(n: Int = 1) = "\u001B[${n}D"

    // Scrolling
    fun scrollUp(n: Int = 1) = "\u001B[${n}S"

    fun scrollDown(n: Int = 1) = "\u001B[${n}T"
}
