package com.ead.dispatch.widget

import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.Whitespace

internal var inputNowNanos: () -> Long = { System.nanoTime() }

/**
 * T2.12 test-only seam: invoked exactly once per *actual* marker render performed by
 * [VerticalCursorMeasurer] (i.e. on each `terminal.render` cache miss, not on cache hits).
 *
 * Mirrors the [inputNowNanos] seam: the default is a no-op, so production behavior is unchanged.
 * It exists because Mordant's `Terminal` (and `Terminal.render`) are `final`, so the render-budget
 * test cannot subclass/decorate the terminal to count calls; this observer is the only behavior-
 * neutral counting point. NOTE: this counts ONLY the editor's vertical-measurement renders, which
 * is exactly the O(n)-per-keypress hot path T2.12 STEP 2 targets.
 */
internal var inputVerticalRenderObserver: () -> Unit = {}

internal data class TextInsertResult(
    val value: String,
    val cursorPosition: Int,
)

internal fun applyInsertion(
    value: String,
    cursorPosition: Int,
    text: String,
): TextInsertResult {
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
    private var preferredVerticalColumn: Int? = null

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
        val key = event.asKeyEvent()
        setCursor(getCursor().coerceIn(0, getValue().length))
        if (key.key != Key.ArrowUp && key.key != Key.ArrowDown) {
            preferredVerticalColumn = null
        }
        when (key.key) {
            Key.PasteStart -> {
                pasteTracker.increment()
                pasteHeuristic.reset()
                return
            }
            Key.PasteEnd -> {
                pasteTracker.decrement()
                pasteHeuristic.reset()
                return
            }
            Key.Enter -> {
                // Shift+Enter inserts a newline (when supported by the terminal).
                if (key.shift || pasteTracker.isActive || pasteHeuristic.shouldTreatEnterAsNewline()) {
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
            Key.Backspace -> {
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
            Key.Delete -> {
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
            Key.ArrowLeft -> {
                val safeCursor = getCursor().coerceIn(0, getValue().length)
                updateCursorPosition((safeCursor - 1).coerceAtLeast(0))
            }
            Key.ArrowRight -> {
                val safeCursor = getCursor().coerceIn(0, getValue().length)
                updateCursorPosition((safeCursor + 1).coerceAtMost(getValue().length))
            }
            Key.ArrowUp -> {
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
                // T2.12 STEP 1: build the shared measurer ONCE and pass it to both
                // cursorLineInfo and moveCursorVertical so they share a single render cache.
                val measurer =
                    VerticalCursorMeasurer(
                        terminal = terminal,
                        text = getValue(),
                        wrapWidth = contentWidthProvider(),
                    )
                val info = cursorLineInfo(measurer, getCursor())
                val targetColumn = preferredVerticalColumn ?: info.col
                preferredVerticalColumn = targetColumn
                if (info.line == 0 && getCursor() > 0) {
                    updateCursorPosition(0)
                    return
                }
                updateCursorPosition(
                    moveCursorVertical(
                        measurer = measurer,
                        cursorPosition = getCursor(),
                        direction = -1,
                        desiredColumn = targetColumn,
                    ),
                )
            }
            Key.ArrowDown -> {
                val historyItems = historyItemsProvider()
                if (historyItems.isNotEmpty() && getCursor() == getValue().length) {
                    historyIndexState.index = historyIndexState.index.coerceIn(0, historyItems.size)
                    if (historyIndexState.index < historyItems.size) {
                        historyIndexState.index =
                            (historyIndexState.index + 1).coerceIn(0, historyItems.size)
                        val next =
                            if (historyIndexState.index == historyItems.size) {
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
                // T2.12 STEP 1: build the shared measurer ONCE and pass it to both
                // cursorLineInfo and moveCursorVertical so they share a single render cache.
                val measurer =
                    VerticalCursorMeasurer(
                        terminal = terminal,
                        text = getValue(),
                        wrapWidth = contentWidthProvider(),
                    )
                val info = cursorLineInfo(measurer, getCursor())
                val targetColumn = preferredVerticalColumn ?: info.col
                preferredVerticalColumn = targetColumn
                if (info.line == info.maxLine && getCursor() < getValue().length) {
                    updateCursorPosition(getValue().length)
                    return
                }
                updateCursorPosition(
                    moveCursorVertical(
                        measurer = measurer,
                        cursorPosition = getCursor(),
                        direction = 1,
                        desiredColumn = targetColumn,
                    ),
                )
            }
            Key.Home -> updateCursorPosition(0)
            Key.End -> updateCursorPosition(getValue().length)
            else -> {
                // Handle printable characters or multi-codepoint text (incl. multi-char paste).
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

internal class PasteHeuristic(
    private val nowNanos: () -> Long,
) {
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

private data class CursorVisual(
    val line: Int,
    val col: Int,
)

private data class CursorLineInfo(
    val line: Int,
    val maxLine: Int,
    val col: Int,
)

/**
 * T2.12 STEP 1 — Shared vertical-cursor measurer.
 *
 * Before T2.12, `cursorLineInfo` and `moveCursorVertical` each declared their OWN `HashMap` cache and
 * their OWN local `visualAt`, so a single ArrowUp/ArrowDown rendered the marker at `clampedCursor` and
 * at `text.length` TWICE (once per function) — 2 fully duplicated `terminal.render` calls per keypress.
 *
 * Hoisting the cache + `visualAt` into one object that both functions share removes those duplicates
 * with NO behavior change. `visualAt` here is byte-identical to the two originals:
 *  - same cursor [CURSOR_MARKER] inserted at the cursor index,
 *  - same [spanSuffixFrom] suffix appended for every marker render,
 *  - same `Whitespace.PRE_WRAP` / `OverflowWrap.BREAK_WORD` / `width.coerceAtLeast(1)`,
 *  - same two index fallbacks: `indexOfFirst { contains(marker) } == -1 -> lines.lastIndex`,
 *    and `markerLine.indexOf(marker) == -1 -> markerLine.length`,
 *  - same per-index memoization keyed by the clamped position.
 */
private class VerticalCursorMeasurer(
    private val terminal: com.github.ajalt.mordant.terminal.Terminal,
    private val text: String,
    wrapWidth: Int,
) {
    private val width = wrapWidth.coerceAtLeast(1)
    private val marker = CURSOR_MARKER
    private val cache = HashMap<Int, CursorVisual>()

    val textLength: Int get() = text.length

    fun visualAt(pos: Int): CursorVisual {
        val safePos = pos.coerceIn(0, text.length)
        return cache.getOrPut(safePos) {
            // Count this as one real terminal.render (cache misses only). No-op in production.
            inputVerticalRenderObserver()
            val prefix = text.substring(0, safePos)
            val suffixSpan = spanSuffixFrom(text, safePos)
            val rendered =
                terminal.render(
                    prefix + marker + suffixSpan,
                    whitespace = Whitespace.PRE_WRAP,
                    overflowWrap = OverflowWrap.BREAK_WORD,
                    width = width,
                )
            val lines = rendered.lines()
            val markerLineIndex =
                lines.indexOfFirst { it.contains(marker) }.let { index ->
                    if (index == -1) lines.lastIndex.coerceAtLeast(0) else index
                }
            val markerLine = lines.getOrNull(markerLineIndex).orEmpty()
            val markerCol =
                markerLine.indexOf(marker).let { index ->
                    if (index == -1) markerLine.length else index
                }

            CursorVisual(line = markerLineIndex, col = markerCol)
        }
    }
}

private fun moveCursorVertical(
    measurer: VerticalCursorMeasurer,
    cursorPosition: Int,
    direction: Int,
    desiredColumn: Int,
): Int {
    val textLength = measurer.textLength
    if (direction == 0) return cursorPosition.coerceIn(0, textLength)

    val clampedCursor = cursorPosition.coerceIn(0, textLength)

    val current = measurer.visualAt(clampedCursor)
    val maxLine = measurer.visualAt(textLength).line
    val targetLine = (current.line + direction).coerceIn(0, maxLine)
    if (targetLine == current.line) return clampedCursor
    val desiredCol = desiredColumn.coerceAtLeast(0)

    // T2.12 STEP 2 DEFERRED: the per-position scan below (one marker `visualAt` for EVERY index
    // 0..textLength) is the O(n^2)-chars-per-keypress hot path the plan targets. The proposed fix is
    // a single no-marker full-text render walked into an index -> (line, col) map, with one marker
    // re-validation render on the chosen candidate. It is NOT shipped here because it cannot be proven
    // BYTE-IDENTICAL without running the renderer (forbidden in this task):
    //   * This scan selects the index whose MARKER render (`prefix + marker + spanSuffixFrom(...)`,
    //     truncated at the cursor span end) lands on `targetLine`, then minimizes `abs(col-desiredCol)`.
    //   * A no-marker render of the WHOLE text can wrap trailing whitespace / a soft-wrap boundary
    //     differently than the truncated marker render (precisely why `spanSuffixFrom` exists). At the
    //     edge cases the golden suite pins — cursor exactly at the wrap column, end-of-logical-line vs
    //     start-of-next-visual-line, trailing spaces — the no-marker map and the marker render can
    //     disagree about which indices sit on `targetLine`.
    //   * Re-validating only the single chosen index cannot reproduce the original min-search's
    //     candidate SET when the map disagrees, so the returned index could differ from master.
    // STEP 1 (shared cache above) is the guaranteed-safe deliverable. STEP 2 should land only once the
    // characterization golden suite (InputEditorVerticalGoldenTest) has CAPTURE-confirmed values on
    // master that the rewrite can be checked against, and the render-budget gate flips green.
    return (0..textLength)
        .asSequence()
        .map { position -> position to measurer.visualAt(position) }
        .filter { (_, visual) -> visual.line == targetLine }
        .minWithOrNull(
            compareBy<Pair<Int, CursorVisual>> { (_, visual) -> kotlin.math.abs(visual.col - desiredCol) }
                .thenByDescending { (position, _) -> position },
        )?.first
        ?: clampedCursor
}

private fun cursorLineInfo(
    measurer: VerticalCursorMeasurer,
    cursorPosition: Int,
): CursorLineInfo {
    val clampedCursor = cursorPosition.coerceIn(0, measurer.textLength)
    val current = measurer.visualAt(clampedCursor)
    val maxLine = measurer.visualAt(measurer.textLength).line
    return CursorLineInfo(line = current.line, maxLine = maxLine, col = current.col)
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
internal fun spanSuffixFrom(
    text: String,
    start: Int,
): String {
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
