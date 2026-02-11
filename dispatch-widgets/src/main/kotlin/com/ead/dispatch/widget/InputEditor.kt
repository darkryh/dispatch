package com.ead.dispatch.widget

import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.Whitespace

internal var inputNowNanos: () -> Long = { System.nanoTime() }

internal data class TextInsertResult(val value: String, val cursorPosition: Int)

internal fun applyInsertion(value: String, cursorPosition: Int, text: String): TextInsertResult {
    val safeCursor = cursorPosition.coerceIn(0, value.length)
    val before = value.substring(0, safeCursor)
    val after = value.substring(safeCursor)
    val nextValue = before + text + after
    return TextInsertResult(nextValue, safeCursor + text.length)
}

internal class InputEditor(
    private val getValue: () -> String,
    private val setValue: (String) -> Unit,
    private val getCursor: () -> Int,
    private val setCursor: (Int) -> Unit,
    private val historyIndexState: InputHistoryIndexState,
    private val pasteTracker: PasteTracker,
    private val pasteHeuristic: PasteHeuristic,
) {
    private var onValueChange: (String) -> Unit = {}
    private var onSubmit: ((String) -> Unit)? = null
    private var onCursorPositionChange: ((Int) -> Unit)? = null
    private var historyItemsProvider: () -> List<String> = { emptyList() }
    private var contentWidthProvider: () -> Int = { 1 }
    private lateinit var terminal: com.github.ajalt.mordant.terminal.Terminal

    fun updateDependencies(
        onValueChange: (String) -> Unit,
        onSubmit: ((String) -> Unit)?,
        onCursorPositionChange: ((Int) -> Unit)?,
        historyItems: () -> List<String>,
        terminal: com.github.ajalt.mordant.terminal.Terminal,
        contentWidth: () -> Int,
    ) {
        this.onValueChange = onValueChange
        this.onSubmit = onSubmit
        this.onCursorPositionChange = onCursorPositionChange
        this.historyItemsProvider = historyItems
        this.terminal = terminal
        this.contentWidthProvider = contentWidth
    }

    fun handleKeyEvent(event: KeyboardEvent) {
        setCursor(getCursor().coerceIn(0, getValue().length))
        when (event.key) {
            "PasteStart" -> {
                pasteTracker.increment()
                pasteHeuristic.reset()
                return
            }
            "PasteEnd" -> {
                pasteTracker.decrement()
                pasteHeuristic.reset()
                return
            }
            "Enter" -> {
                // Shift+Enter inserts a newline (when supported by the terminal).
                if (event.shift || pasteTracker.isActive || pasteHeuristic.shouldTreatEnterAsNewline()) {
                    insertText("\n")
                    return
                }

                val submit = onSubmit
                if (submit != null) {
                    val text = getValue()
                    if (text.isNotBlank()) {
                        submit(text)
                        setValue("")
                        updateCursorPosition(0)
                        onValueChange("")
                    }
                }
            }
            "Backspace" -> {
                val currentValue = getValue()
                val safeCursor = getCursor().coerceIn(0, currentValue.length)
                if (safeCursor > 0 && currentValue.isNotEmpty()) {
                    val before = currentValue.substring(0, safeCursor - 1)
                    val after = currentValue.substring(safeCursor)
                    setValue(before + after)
                    val historyItems = historyItemsProvider()
                    if (historyItems.isNotEmpty() && historyIndexState.index < historyItems.size) {
                        historyIndexState.draft = getValue()
                        historyIndexState.index = historyItems.size
                    }
                    updateCursorPosition(safeCursor - 1)
                    onValueChange(getValue())
                } else {
                    updateCursorPosition(safeCursor)
                }
            }
            "Delete" -> {
                val currentValue = getValue()
                val safeCursor = getCursor().coerceIn(0, currentValue.length)
                if (safeCursor < currentValue.length && currentValue.isNotEmpty()) {
                    val before = currentValue.substring(0, safeCursor)
                    val after = currentValue.substring(safeCursor + 1)
                    setValue(before + after)
                    val historyItems = historyItemsProvider()
                    if (historyItems.isNotEmpty() && historyIndexState.index < historyItems.size) {
                        historyIndexState.draft = getValue()
                        historyIndexState.index = historyItems.size
                    }
                    onValueChange(getValue())
                } else {
                    updateCursorPosition(safeCursor)
                }
            }
            "ArrowLeft" -> {
                val safeCursor = getCursor().coerceIn(0, getValue().length)
                updateCursorPosition((safeCursor - 1).coerceAtLeast(0))
            }
            "ArrowRight" -> {
                val safeCursor = getCursor().coerceIn(0, getValue().length)
                updateCursorPosition((safeCursor + 1).coerceAtMost(getValue().length))
            }
            "ArrowUp" -> {
                val historyItems = historyItemsProvider()
                if (historyItems.isNotEmpty() && getCursor() == 0) {
                    historyIndexState.index = historyIndexState.index.coerceIn(0, historyItems.size)
                    if (historyIndexState.index == historyItems.size) {
                        historyIndexState.draft = getValue()
                    }
                    historyIndexState.index = (historyIndexState.index - 1).coerceIn(0, historyItems.lastIndex)
                    val previous = historyItems.getOrNull(historyIndexState.index) ?: return
                    setValue(previous)
                    updateCursorPosition(previous.length)
                    onValueChange(getValue())
                    return
                }
                val info = cursorLineInfo(
                    terminal = terminal,
                    text = getValue(),
                    cursorPosition = getCursor(),
                    wrapWidth = contentWidthProvider(),
                )
                if (info.line == 0 && getCursor() > 0) {
                    updateCursorPosition(0)
                    return
                }
                updateCursorPosition(
                    moveCursorVertical(
                        terminal = terminal,
                        text = getValue(),
                        cursorPosition = getCursor(),
                        direction = -1,
                        wrapWidth = contentWidthProvider(),
                    )
                )
            }
            "ArrowDown" -> {
                val historyItems = historyItemsProvider()
                if (historyItems.isNotEmpty() && getCursor() == getValue().length) {
                    historyIndexState.index = historyIndexState.index.coerceIn(0, historyItems.size)
                    if (historyIndexState.index < historyItems.size) {
                        historyIndexState.index =
                            (historyIndexState.index + 1).coerceIn(0, historyItems.size)
                        val next = if (historyIndexState.index == historyItems.size) {
                            historyIndexState.draft ?: ""
                        } else {
                            historyItems.getOrElse(historyIndexState.index) { "" }
                        }
                        setValue(next)
                        updateCursorPosition(next.length)
                        onValueChange(getValue())
                        return
                    }
                }
                val info = cursorLineInfo(
                    terminal = terminal,
                    text = getValue(),
                    cursorPosition = getCursor(),
                    wrapWidth = contentWidthProvider(),
                )
                if (info.line == info.maxLine && getCursor() < getValue().length) {
                    updateCursorPosition(getValue().length)
                    return
                }
                updateCursorPosition(
                    moveCursorVertical(
                        terminal = terminal,
                        text = getValue(),
                        cursorPosition = getCursor(),
                        direction = 1,
                        wrapWidth = contentWidthProvider(),
                    )
                )
            }
            "Home" -> updateCursorPosition(0)
            "End" -> updateCursorPosition(getValue().length)
            else -> {
                // Handle printable characters or multi-codepoint text
                val text = parseTextFromKeyEvent(event)
                if (text != null) {
                    insertText(text)
                } else {
                    pasteHeuristic.reset()
                }
            }
        }
    }

    private fun updateCursorPosition(nextPosition: Int) {
        val bounded = nextPosition.coerceIn(0, getValue().length)
        if (getCursor() != bounded) {
            setCursor(bounded)
            onCursorPositionChange?.invoke(bounded)
        }
    }

    private fun insertText(text: String) {
        if (text.isEmpty()) return
        val historyItems = historyItemsProvider()
        if (historyItems.isNotEmpty() && historyIndexState.index < historyItems.size) {
            historyIndexState.draft = getValue()
            historyIndexState.index = historyItems.size
        }
        val result = applyInsertion(getValue(), getCursor(), text)
        setValue(result.value)
        updateCursorPosition(result.cursorPosition)
        onValueChange(getValue())
        pasteHeuristic.recordTextInsert(text)
    }
}

internal class PasteTracker {
    private var depth = 0
    val isActive: Boolean get() = depth > 0

    fun increment() {
        depth += 1
    }

    fun decrement() {
        depth = (depth - 1).coerceAtLeast(0)
    }
}

internal class PasteHeuristic(private val nowNanos: () -> Long) {
    private var lastTextAtNanos = 0L
    private var recentInsertCount = 0
    private var suppressNextEnterUntilNanos = 0L

    fun recordTextInsert(text: String) {
        val now = nowNanos()
        val withinBurst = (now - lastTextAtNanos) <= PASTE_BURST_NANOS
        recentInsertCount = if (withinBurst) recentInsertCount + 1 else 1
        lastTextAtNanos = now
        if (text.length > 1 || text.contains('\n')) {
            suppressNextEnterUntilNanos =
                maxOf(suppressNextEnterUntilNanos, now + PASTE_SUPPRESS_LONG_NANOS)
        } else if (recentInsertCount >= PASTE_BURST_COUNT) {
            suppressNextEnterUntilNanos =
                maxOf(suppressNextEnterUntilNanos, now + PASTE_SUPPRESS_SHORT_NANOS)
        }
    }

    fun shouldTreatEnterAsNewline(): Boolean {
        val now = nowNanos()
        if (now <= suppressNextEnterUntilNanos) {
            suppressNextEnterUntilNanos = 0L
            recentInsertCount = 0
            return true
        }
        if (recentInsertCount == 0) return false
        val withinBurst = (now - lastTextAtNanos) <= PASTE_BURST_NANOS
        recentInsertCount = 0
        return withinBurst
    }

    fun reset() {
        recentInsertCount = 0
        suppressNextEnterUntilNanos = 0L
    }
}

private data class CursorVisual(val line: Int, val col: Int)

private data class CursorLineInfo(val line: Int, val maxLine: Int)

private fun moveCursorVertical(
    terminal: com.github.ajalt.mordant.terminal.Terminal,
    text: String,
    cursorPosition: Int,
    direction: Int,
    wrapWidth: Int,
): Int {
    if (direction == 0) return cursorPosition.coerceIn(0, text.length)

    val width = wrapWidth.coerceAtLeast(1)
    val clampedCursor = cursorPosition.coerceIn(0, text.length)
    val cache = HashMap<Int, CursorVisual>()
    val marker = CURSOR_MARKER

    fun visualAt(pos: Int): CursorVisual {
        val safePos = pos.coerceIn(0, text.length)
        return cache.getOrPut(safePos) {
            val prefix = text.substring(0, safePos)
            val suffixSpan = spanSuffixFrom(text, safePos)
            val rendered = terminal.render(
                prefix + marker + suffixSpan,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = width,
            )
            val lines = rendered.lines()
            val markerLineIndex = lines.indexOfFirst { it.contains(marker) }.let { index ->
                if (index == -1) lines.lastIndex.coerceAtLeast(0) else index
            }
            val markerLine = lines.getOrNull(markerLineIndex).orEmpty()
            val markerCol = markerLine.indexOf(marker).let { index ->
                if (index == -1) markerLine.length else index
            }

            CursorVisual(line = markerLineIndex, col = markerCol)
        }
    }

    val current = visualAt(clampedCursor)
    val maxLine = visualAt(text.length).line
    val targetLine = (current.line + direction).coerceIn(0, maxLine)
    if (targetLine == current.line) return clampedCursor
    val desiredCol = current.col

    fun lowerBoundLine(target: Int): Int {
        var low = 0
        var high = text.length + 1 // exclusive
        while (low < high) {
            val mid = (low + high) / 2
            val line = visualAt(mid.coerceAtMost(text.length)).line
            if (line < target) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return low.coerceIn(0, text.length)
    }

    val start = lowerBoundLine(targetLine)
    val endExclusive = lowerBoundLine(targetLine + 1).coerceAtMost(text.length)
    if (start >= endExclusive) return clampedCursor

    var low = start
    var high = endExclusive
    while (low < high) {
        val mid = (low + high) / 2
        val v = visualAt(mid)
        when {
            v.line > targetLine -> high = mid
            v.col <= desiredCol -> low = mid + 1
            else -> high = mid
        }
    }

    return (low - 1).coerceIn(start, endExclusive - 1)
}

private fun cursorLineInfo(
    terminal: com.github.ajalt.mordant.terminal.Terminal,
    text: String,
    cursorPosition: Int,
    wrapWidth: Int,
): CursorLineInfo {
    val width = wrapWidth.coerceAtLeast(1)
    val clampedCursor = cursorPosition.coerceIn(0, text.length)
    val cache = HashMap<Int, CursorVisual>()
    val marker = CURSOR_MARKER

    fun visualAt(pos: Int): CursorVisual {
        val safePos = pos.coerceIn(0, text.length)
        return cache.getOrPut(safePos) {
            val prefix = text.substring(0, safePos)
            val suffixSpan = spanSuffixFrom(text, safePos)
            val rendered = terminal.render(
                prefix + marker + suffixSpan,
                whitespace = Whitespace.PRE_WRAP,
                overflowWrap = OverflowWrap.BREAK_WORD,
                width = width,
            )
            val lines = rendered.lines()
            val markerLineIndex = lines.indexOfFirst { it.contains(marker) }.let { index ->
                if (index == -1) lines.lastIndex.coerceAtLeast(0) else index
            }
            val markerLine = lines.getOrNull(markerLineIndex).orEmpty()
            val markerCol = markerLine.indexOf(marker).let { index ->
                if (index == -1) markerLine.length else index
            }
            CursorVisual(line = markerLineIndex, col = markerCol)
        }
    }

    val current = visualAt(clampedCursor)
    val maxLine = visualAt(text.length).line
    return CursorLineInfo(line = current.line, maxLine = maxLine)
}

private const val PASTE_BURST_NANOS: Long = 35_000_000
private const val PASTE_BURST_COUNT: Int = 3
private const val PASTE_SUPPRESS_SHORT_NANOS: Long = 150_000_000
private const val PASTE_SUPPRESS_LONG_NANOS: Long = 600_000_000

/**
 * Cursor marker used for measurement only.
 *
 * Use a control character (cell width = 0 in Mordant) so it doesn't affect wrapping decisions.
 */
internal const val CURSOR_MARKER: String = "\u0001"

/**
 * Return the suffix of the "current span" starting at [start], using the same span boundaries as
 * Mordant's text parser (whitespace vs. non-whitespace, plus hard-break characters).
 *
 * Including this suffix when measuring cursor position prevents incorrect wrapping when the cursor
 * is inside a word that would otherwise be wrapped as a whole.
 */
internal fun spanSuffixFrom(text: String, start: Int): String {
    if (start >= text.length) return ""

    val type = cursorSpanType(text[start])
    if (type == 1) return text[start].toString()

    var end = start + 1
    while (end < text.length && cursorSpanType(text[end]) == type) {
        end += 1
    }
    return text.substring(start, end)
}

private fun cursorSpanType(c: Char): Int {
    // Mirror Mordant's Parsing.splitWords types:
    // 0 = carriage return, 1 = always-break chunk, 2 = whitespace, 3 = word
    return when {
        c == '\r' -> 0
        c == '\n' || c == '\t' || c == '\u0085' || c == '\u2028' -> 1
        c.isWhitespace() -> 2
        else -> 3
    }
}
