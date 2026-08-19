package io.github.darkryh.dispatch.vt

/**
 * A mutable terminal screen: a cell grid, a cursor, and a scrollback.
 *
 * This models the subset of terminal behaviour Dispatch actually depends on, with two details that
 * the rest of the project's tests ignore and that matter a great deal here:
 *
 * - **Deferred wrap.** Writing to the last column does not move the cursor to the next row; it sets
 *   [pendingWrap], and the wrap happens on the *next* printable character. Dispatch pads every row
 *   to exactly the terminal width, so every row it paints ends in this state. Getting it wrong
 *   would desynchronise every subsequent row by one.
 * - **Scrollback.** Lines pushed above the top of the viewport are retained in [scrollback], so a
 *   test can assert that history is append-only and that a scrollback wipe was declared.
 */
class VtScreen(
    width: Int,
    height: Int,
) {
    var width: Int = width
        private set

    var height: Int = height
        private set

    private var grid: Array<Array<Cell>> = blankGrid(width, height)

    /** Lines that have scrolled off the top of the viewport, oldest first. */
    private val scrollbackLines: MutableList<List<Cell>> = mutableListOf()

    /** Read-only view of the retained history. */
    val scrollback: List<List<Cell>> get() = scrollbackLines

    var cursorRow: Int = 0
        private set

    var cursorCol: Int = 0
        private set

    /** Set after writing the last column; the wrap is applied on the next printable character. */
    var pendingWrap: Boolean = false
        private set

    var cursorVisible: Boolean = true
        internal set

    /** DECAWM. Dispatch never changes this, so it stays at the terminal default. */
    var autoWrap: Boolean = true
        internal set

    /** DEC 2026 synchronized output. True between BSU and ESU. */
    var synchronizedUpdate: Boolean = false
        internal set

    /** DECSET 1049. Dispatch must never enable this; the invariants assert it. */
    var alternateScreen: Boolean = false
        internal set

    /** DECSTBM top/bottom margins, 0-based inclusive. Null when unset (the whole screen scrolls). */
    var scrollRegion: IntRange? = null
        internal set

    var style: CellStyle = CellStyle.DEFAULT
        internal set

    /** Count of scrollback-clearing operations (`ESC[3J`) applied to this screen. */
    var scrollbackClearCount: Int = 0
        private set

    private val touched: MutableSet<Int> = mutableSetOf()

    /**
     * Rows written to or erased since the last [resetDamage].
     *
     * This is the numerator of the damage ratio: a renderer that rewrites forty rows to move a
     * selection by one is doing twenty times the work the change required, and every one of those
     * rewritten rows is a row that can be caught mid-repaint.
     */
    val touchedRows: Set<Int> get() = touched

    /** Wraps caused by content exceeding the row width rather than by an explicit line feed. */
    var implicitWraps: Int = 0
        private set

    /** Clears the per-frame damage counters. */
    fun resetDamage() {
        touched.clear()
        implicitWraps = 0
    }

    // ---------------------------------------------------------------- reading

    fun cellAt(row: Int, column: Int): Cell =
        if (row in 0 until height && column in 0 until width) grid[row][column] else Cell.BLANK

    fun row(row: Int): List<Cell> = if (row in 0 until height) grid[row].toList() else emptyList()

    /** The row rendered as text, trailing blanks preserved so column positions stay meaningful. */
    fun rowText(row: Int): String =
        buildString {
            if (row !in 0 until height) return@buildString
            for (cell in grid[row]) {
                if (cell.continuation) continue
                append(cell.toChar())
            }
        }

    /** True when every cell in the row shows nothing at all. */
    fun isRowBlank(row: Int): Boolean = row in 0 until height && grid[row].all { it.isBlank }

    // ---------------------------------------------------------------- writing

    /**
     * Write one code point at the cursor, applying deferred wrap and double-width handling.
     */
    fun print(codePoint: Int) {
        val cellWidth = Wcwidth.width(codePoint)
        if (cellWidth == 0) {
            // Combining mark: it belongs to the previous cell and consumes no column.
            return
        }

        if (pendingWrap && autoWrap) {
            implicitWraps += 1
            carriageReturn()
            lineFeed()
        }
        pendingWrap = false

        if (cursorCol + cellWidth > width) {
            if (!autoWrap) return
            implicitWraps += 1
            carriageReturn()
            lineFeed()
        }

        touched += cursorRow
        grid[cursorRow][cursorCol] = Cell(codePoint, style, continuation = false)
        if (cellWidth == 2 && cursorCol + 1 < width) {
            grid[cursorRow][cursorCol + 1] = Cell(codePoint, style, continuation = true)
        }

        val next = cursorCol + cellWidth
        if (next >= width) {
            cursorCol = width - 1
            pendingWrap = true
        } else {
            cursorCol = next
        }
    }

    fun carriageReturn() {
        cursorCol = 0
        pendingWrap = false
    }

    fun lineFeed() {
        pendingWrap = false
        val region = scrollRegion
        val bottom = region?.last ?: (height - 1)
        if (cursorRow >= bottom) {
            scrollUp(1)
        } else {
            cursorRow += 1
        }
    }

    fun backspace() {
        pendingWrap = false
        if (cursorCol > 0) cursorCol -= 1
    }

    fun tab() {
        pendingWrap = false
        val next = ((cursorCol / TAB_STOP) + 1) * TAB_STOP
        cursorCol = next.coerceAtMost(width - 1)
    }

    // ---------------------------------------------------------------- cursor

    /** Absolute position, 0-based, clamped to the screen. */
    fun setPosition(row: Int, column: Int) {
        cursorRow = row.coerceIn(0, height - 1)
        cursorCol = column.coerceIn(0, width - 1)
        pendingWrap = false
    }

    fun moveUp(n: Int) = setPosition(cursorRow - n, cursorCol)

    fun moveDown(n: Int) = setPosition(cursorRow + n, cursorCol)

    fun moveRight(n: Int) = setPosition(cursorRow, cursorCol + n)

    fun moveLeft(n: Int) = setPosition(cursorRow, cursorCol - n)

    fun setColumn(column: Int) = setPosition(cursorRow, column)

    fun setRow(row: Int) = setPosition(row, cursorCol)

    // ---------------------------------------------------------------- erasing

    /**
     * ED — Erase in Display.
     *
     * @param mode 0 = cursor to end, 1 = start to cursor, 2 = whole screen, 3 = scrollback.
     */
    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseInLine(0)
                for (r in cursorRow + 1 until height) blankRow(r)
            }
            1 -> {
                eraseInLine(1)
                for (r in 0 until cursorRow) blankRow(r)
            }
            2 -> for (r in 0 until height) blankRow(r)
            3 -> {
                // ESC[3J — Erase Saved Lines. Destroys the terminal's scrollback buffer.
                scrollbackLines.clear()
                scrollbackClearCount += 1
            }
            else -> Unit
        }
        pendingWrap = false
    }

    /**
     * EL — Erase in Line.
     *
     * @param mode 0 = cursor to end, 1 = start to cursor, 2 = whole line.
     */
    fun eraseInLine(mode: Int) {
        val row = grid[cursorRow]
        val range =
            when (mode) {
                0 -> cursorCol until width
                1 -> 0..cursorCol.coerceAtMost(width - 1)
                2 -> 0 until width
                else -> IntRange.EMPTY
            }
        if (!range.isEmpty()) touched += cursorRow
        for (c in range) row[c] = Cell.BLANK
        pendingWrap = false
    }

    // ---------------------------------------------------------------- scrolling

    /** SU — scroll the region up [n] lines, pushing displaced top lines into [scrollback]. */
    fun scrollUp(n: Int) {
        val region = scrollRegion
        val top = region?.first ?: 0
        val bottom = region?.last ?: (height - 1)
        repeat(n.coerceAtLeast(0)) {
            // Only lines leaving the *screen* top enter scrollback; a DECSTBM region discards them.
            if (region == null) scrollbackLines += grid[top].toList()
            for (r in top until bottom) grid[r] = grid[r + 1]
            grid[bottom] = blankRow()
        }
    }

    /** SD — scroll the region down [n] lines. Lines pushed off the bottom are discarded. */
    fun scrollDown(n: Int) {
        val region = scrollRegion
        val top = region?.first ?: 0
        val bottom = region?.last ?: (height - 1)
        repeat(n.coerceAtLeast(0)) {
            for (r in bottom downTo top + 1) grid[r] = grid[r - 1]
            grid[top] = blankRow()
        }
    }

    /** IL — insert [n] blank lines at the cursor row, within the scroll region. */
    fun insertLines(n: Int) {
        val bottom = scrollRegion?.last ?: (height - 1)
        repeat(n.coerceAtLeast(0)) {
            for (r in bottom downTo cursorRow + 1) grid[r] = grid[r - 1]
            grid[cursorRow] = blankRow()
        }
    }

    /** DL — delete [n] lines at the cursor row, within the scroll region. */
    fun deleteLines(n: Int) {
        val bottom = scrollRegion?.last ?: (height - 1)
        repeat(n.coerceAtLeast(0)) {
            for (r in cursorRow until bottom) grid[r] = grid[r + 1]
            grid[bottom] = blankRow()
        }
    }

    // ---------------------------------------------------------------- geometry

    /**
     * Resize the viewport.
     *
     * Rows are preserved top-anchored and truncated or padded; this matches what a terminal does
     * closely enough for the invariants, which never assert across a resize boundary.
     */
    fun resize(newWidth: Int, newHeight: Int) {
        val next = blankGrid(newWidth, newHeight)
        for (r in 0 until minOf(height, newHeight)) {
            for (c in 0 until minOf(width, newWidth)) {
                next[r][c] = grid[r][c]
            }
        }
        grid = next
        width = newWidth
        height = newHeight
        cursorRow = cursorRow.coerceIn(0, newHeight - 1)
        cursorCol = cursorCol.coerceIn(0, newWidth - 1)
        pendingWrap = false
    }

    /** An immutable capture of the current visible state. */
    fun snapshot(): ScreenSnapshot =
        ScreenSnapshot(
            width = width,
            height = height,
            rows = List(height) { grid[it].toList() },
            cursorRow = cursorRow,
            cursorCol = cursorCol,
            cursorVisible = cursorVisible,
            scrollbackSize = scrollbackLines.size,
            scrollbackClearCount = scrollbackClearCount,
        )

    // ---------------------------------------------------------------- internals

    private fun blankRow(): Array<Cell> = Array(width) { Cell.BLANK }

    private fun blankRow(row: Int) {
        touched += row
        grid[row] = blankRow()
    }

    private companion object {
        const val TAB_STOP = 8

        fun blankGrid(width: Int, height: Int): Array<Array<Cell>> =
            Array(height) { Array(width) { Cell.BLANK } }
    }
}
