package io.github.darkryh.dispatch.render

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
     * Reused frame buffer. Every emitting method builds into this instead of allocating a fresh
     * StringBuilder per call. Only ever touched while holding [renderLock], and [flushBuffer]
     * consumes it synchronously before the lock is released, so it is never aliased across frames.
     * It retains the largest frame's capacity (a deliberate steady-state memory tradeoff).
     */
    private val scratchBuffer = StringBuilder(256)

    private fun scratch(): StringBuilder {
        scratchBuffer.setLength(0)
        return scratchBuffer
    }

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
            // This path writes rows the viewport cache does not know about.
            dropViewportDiff()
            val buffer = scratch()
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
        eraseScreen: Boolean = clearScrollback,
    ) {
        renderLock.withLock {
            val buffer = scratch()
            if (clearScrollback) {
                buffer.append(AnsiCodes.CLEAR_SCROLLBACK)
            }
            buffer.append(AnsiCodes.CURSOR_HOME)
            if (eraseScreen) {
                // Screen transition / content reset: wipe the whole visible screen from the
                // top-left before repainting. The per-line CLEAR_LINE below and
                // clearViewportRowsAfter only erase rows the *new* frame addresses; but this app
                // renders inline (no alternate screen buffer) and CURSOR_HOME is an absolute move
                // to physical row 1. If the terminal scrolled while the previous, taller screen
                // was visible, that screen's rows now sit at physical offsets the new frame never
                // overwrites — leaving ghost fragments (the cross-screen residue seen on short
                // terminal windows). CLEAR_TO_END erases to end-of-screen independent of any
                // scroll offset. It is emitted in the same atomic buffer as the repaint that
                // immediately follows, so the terminal never composites a blank frame; and it is
                // gated on eraseScreen, so same-screen overlay rewrites keep their per-line diff
                // path. Note this erases the *visible screen* only; destroying the scrollback is a
                // separate decision (clearScrollback / ESC[3J) because the two serve different
                // purposes and conflating them wiped history on every resize.
                buffer.append(AnsiCodes.CLEAR_TO_END)
            }
            val contentLineCount = scrollingLines.size + activeLines.size
            val lines = scrollingLines + activeLines
            // The operation name stays "rewrite_viewport" in both cases: the caller asked for a
            // viewport rewrite and got one. Which path served it is a detail, reported separately
            // so it can be measured without changing the write's identity.
            val diffed = appendViewportDiff(buffer, lines, eraseScreen)
            if (!diffed) {
                appendViewportLinesWithoutTrailingNewline(buffer, scrollingLines, activeLines)
                clearViewportRowsAfter(buffer, contentLineCount)
            }
            if (RenderDiagnostics.isEnabled) {
                RenderDiagnostics.record(
                    "viewport_repaint",
                    mapOf(
                        "path" to if (diffed) "diff" else "full",
                        "rows" to contentLineCount,
                    ),
                )
            }
            if (contentLineCount > 0) {
                buffer.append(AnsiCodes.moveTo(contentLineCount.coerceAtMost(terminalHeight), 1))
            }
            flushBuffer(buffer, "rewrite_viewport")

            rememberViewport(lines)
            activeAreaLines = activeLines
            activeAreaInitialized = activeLines.isNotEmpty()
        }
    }

    /**
     * Last full viewport painted by [rewriteViewport], and the geometry it was painted at.
     *
     * Held so a repaint can address only the rows that actually changed. Without it every viewport
     * rewrite repaints every visible row — which is how moving a selection one row down came to
     * cost a forty-row repaint.
     */
    private var lastViewportLines: List<String> = emptyList()
    private var lastViewportWidth: Int = -1
    private var lastViewportHeight: Int = -1

    /**
     * Whether an active-area update can supersede the tail of the cached viewport instead of
     * invalidating it.
     *
     * Requires the active area to keep its size (otherwise the tail no longer aligns) and the
     * viewport to fit the screen at unchanged geometry (otherwise the write can scroll the terminal
     * and the cache's absolute row mapping stops being true).
     */
    private fun canPatchViewportTail(lines: List<String>): Boolean {
        if (lastViewportLines.isEmpty()) return false
        if (lines.size != activeAreaLines.size) return false
        if (lastViewportLines.size < lines.size) return false
        if (lastViewportLines.size > terminalHeight) return false
        return lastViewportWidth == terminalWidth && lastViewportHeight == terminalHeight
    }

    /** Discards the diff cache. Callers already hold [renderLock]. */
    private fun dropViewportDiff() {
        lastViewportLines = emptyList()
        lastViewportWidth = -1
        lastViewportHeight = -1
    }

    private fun rememberViewport(lines: List<String>) {
        lastViewportLines = lines
        lastViewportWidth = terminalWidth
        lastViewportHeight = terminalHeight
    }

    /** Drops the viewport diff cache, forcing the next rewrite to repaint in full. */
    fun invalidateViewportDiff() {
        renderLock.withLock { dropViewportDiff() }
    }

    /**
     * Emit only the rows that differ from the last painted viewport.
     *
     * Returns false when a full repaint is required instead — on a scrollback-clearing transition,
     * after a resize, with no previous frame to diff against, or when the content is taller than
     * the screen (at which point the sequential `\n` path scrolls the terminal and absolute row
     * addressing no longer refers to the row it names).
     *
     * Rows are written with an absolute cursor move and no erase: the layout engine pads every line
     * to exactly the terminal width, so writing the line overwrites the row completely. The erase
     * was redundant, and it was the thing that made a torn frame show a blank row rather than stale
     * text.
     */
    private fun appendViewportDiff(
        buffer: StringBuilder,
        lines: List<String>,
        eraseScreen: Boolean,
    ): Boolean {
        if (eraseScreen) return false
        if (lastViewportLines.isEmpty()) return false
        if (lastViewportWidth != terminalWidth || lastViewportHeight != terminalHeight) return false
        val height = terminalHeight
        if (lines.size > height || lastViewportLines.size > height) return false

        val rowCount = maxOf(lines.size, lastViewportLines.size)
        for (row in 0 until rowCount) {
            val next = lines.getOrNull(row)
            val previous = lastViewportLines.getOrNull(row)
            if (next == previous) continue
            AnsiCodes.appendMoveTo(buffer, row + 1, 1)
            if (next == null) {
                // The viewport shrank: this row held content last frame and holds none now.
                buffer.append(AnsiCodes.CLEAR_LINE)
            } else {
                buffer.append(next)
            }
        }
        return true
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
            // The active area is the tail of the viewport, so this path does not invalidate the
            // whole diff cache — it supersedes the rows it owns. Patching the tail instead of
            // dropping the cache is what keeps a following viewport rewrite on the diff path;
            // dropping it here forced a full-screen repaint after every keystroke that touched the
            // input row. Guarded: only when the active area keeps its size and the viewport fits
            // the screen, since a resized or overflowing active area can scroll the terminal and
            // invalidate the absolute row mapping the diff relies on.
            val cached = lastViewportLines
            if (canPatchViewportTail(lines)) {
                lastViewportLines = cached.dropLast(lines.size) + lines
            } else {
                dropViewportDiff()
            }
            // We assume the layout engine has already constrained the lines to the terminal width.
            // Naive truncation with take() breaks ANSI escape codes.
            val displayedLines = lines

            val hasAnyContentChange = displayedLines != activeAreaLines
            // Skip if nothing changed
            if (!hasAnyContentChange && activeAreaInitialized) return

            val oldLineCount = if (activeAreaInitialized) activeAreaLines.size else 0
            val newLineCount = displayedLines.size
            val maxLineCount = maxOf(oldLineCount, newLineCount)
            // Repaint every row only when the active area CHANGES SHAPE. A shape change moves the
            // relative anchor this path positions from, so redrawing the siblings is what keeps
            // them stable when an external input method momentarily desynchronizes output.
            //
            // Previously this also forced a full repaint whenever *any* line differed, which made
            // the per-line comparison below unreachable — the method already returns early when
            // nothing changed. That cost the whole active area on every keystroke, and for an app
            // that sets activeAreaHeight to the terminal height (so the entire viewport IS the
            // active area) it meant repainting all 29 rows to change 2. Row advance below is a
            // newline emitted unconditionally, so skipping an unchanged row's content is safe.
            val forceRedraw = oldLineCount != newLineCount

            // Build entire update in a buffer to send atomically (prevents flickering)
            val buffer = scratch()
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
            // This path writes rows the viewport cache does not know about.
            dropViewportDiff()
            if (activeAreaInitialized && activeAreaLines.isNotEmpty()) {
                val buffer = scratch()
                clearActiveAreaInto(buffer)
                flushBuffer(buffer, "clear_active_area")
            }
            activeAreaLines = emptyList()
            activeAreaInitialized = false
            dropViewportDiff()
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
            // This path writes rows the viewport cache does not know about.
            dropViewportDiff()
            val buffer = scratch()
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
            else -> {}
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
        if (newLineCount in 1..<oldLineCount) {
            buffer.append(AnsiCodes.moveUp(oldLineCount - newLineCount))
        }
    }

    private fun flushBuffer(
        buffer: StringBuilder,
        operation: String,
    ) {
        val startedAt = if (RenderDiagnostics.isEnabled) System.nanoTime() else 0L
        OutputCapture.suppress {
            // One frame, one atomic presentation: the sync guard stops the terminal compositing a
            // half-applied frame, and FrameOutput's buffer stops the JDK splitting it into several
            // write(2) calls. Both are needed — the guard cannot help if the bytes arrive late, and
            // a single write still races the terminal's refresh tick without the guard.
            terminal.rawPrint(
                buildString(buffer.length + AnsiCodes.SYNC_BEGIN.length + AnsiCodes.SYNC_END.length) {
                    append(AnsiCodes.SYNC_BEGIN)
                    append(buffer)
                    append(AnsiCodes.SYNC_END)
                },
            )
            System.out.flush()
        }
        // The field map below scans and re-copies the whole frame (toString()/toByteArray()/
        // contains/countOccurrences). None of it touches the terminal, so skip it entirely when
        // diagnostics are off (the production default).
        if (RenderDiagnostics.isEnabled) {
            RenderDiagnostics.record(
                event = "terminal_write",
                fields =
                    mapOf(
                        "operation" to operation,
                        "bytes" to buffer.toString().toByteArray(Charsets.UTF_8).size,
                        "chars" to buffer.length,
                        "clearScreen" to buffer.contains(AnsiCodes.CLEAR_SCREEN),
                        "clearToEnd" to buffer.contains(AnsiCodes.CLEAR_TO_END),
                        "clearScrollback" to buffer.contains(AnsiCodes.CLEAR_SCROLLBACK),
                        "clearLines" to buffer.countOccurrences(AnsiCodes.CLEAR_LINE),
                        "durationNanos" to System.nanoTime() - startedAt,
                    ),
            )
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
        // Serialize the BEL with frame emission via renderLock; without it the BEL byte can
        // interleave into the middle of another method's atomic frame write (the single-writer
        // invariant every other emitting path already upholds).
        renderLock.withLock {
            OutputCapture.suppress {
                terminal.rawPrint("\u0007")
                System.out.flush()
            }
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

private fun List<String>.trailingBlankLineCount(): Int = asReversed().takeWhile { it.isDisplayBlank() }.count()

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

private fun ansiEscapeLength(
    text: String,
    start: Int,
): Int {
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

    // Synchronized output (DEC 2026). Wrapping a frame in these tells the terminal to buffer the
    // whole update and present it in one composite, instead of painting whatever has arrived when
    // its refresh tick fires. Terminals that do not implement it ignore the private mode, so this is
    // safe to emit unconditionally. It has no effect on scrollback or on native scrolling.
    const val SYNC_BEGIN = "\u001B[?2026h"
    const val SYNC_END = "\u001B[?2026l"

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
        buffer
            .append("\u001B[")
            .append(row)
            .append(';')
            .append(col)
            .append('H')
    }

    fun moveUp(n: Int = 1) = if (n == 1) MOVE_UP_1 else "\u001B[${n}A"

    fun moveDown(n: Int = 1) = if (n == 1) MOVE_DOWN_1 else "\u001B[${n}B"

    fun moveRight(n: Int = 1) = "\u001B[${n}C"

    fun moveLeft(n: Int = 1) = "\u001B[${n}D"

    // Scrolling
    fun scrollUp(n: Int = 1) = "\u001B[${n}S"

    fun scrollDown(n: Int = 1) = "\u001B[${n}T"
}
