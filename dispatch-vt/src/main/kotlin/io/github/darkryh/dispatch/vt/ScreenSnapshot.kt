package io.github.darkryh.dispatch.vt

/**
 * An immutable capture of what the screen showed at one instant.
 *
 * Snapshots are the unit the render invariants are written against: a frame is judged by comparing
 * the snapshot before it, the snapshots at each point the terminal could have painted mid-frame,
 * and the snapshot after it.
 */
data class ScreenSnapshot(
    val width: Int,
    val height: Int,
    val rows: List<List<Cell>>,
    val cursorRow: Int,
    val cursorCol: Int,
    val cursorVisible: Boolean,
    val scrollbackSize: Int,
    val scrollbackClearCount: Int,
) {
    /** Number of cells that show nothing at all. */
    val blankCells: Int get() = rows.sumOf { row -> row.count { it.isBlank } }

    /** Number of cells showing something. */
    val paintedCells: Int get() = width * height - blankCells

    fun isRowBlank(row: Int): Boolean = row in rows.indices && rows[row].all { it.isBlank }

    /** Indices of every row that shows nothing. */
    fun blankRows(): List<Int> = rows.indices.filter { isRowBlank(it) }

    fun rowText(row: Int): String =
        if (row !in rows.indices) {
            ""
        } else {
            buildString {
                for (cell in rows[row]) {
                    if (cell.continuation) continue
                    append(cell.toChar())
                }
            }
        }

    /** The whole screen as text, one row per line. */
    fun render(): String = rows.indices.joinToString("\n") { rowText(it) }

    /**
     * Cells whose content or style differs from [other].
     *
     * Screens of differing geometry are reported as wholly different, since no cell-wise
     * correspondence is meaningful across a resize.
     */
    fun changedCells(other: ScreenSnapshot): Int {
        if (width != other.width || height != other.height) return width * height
        var count = 0
        for (r in 0 until height) {
            val a = rows[r]
            val b = other.rows[r]
            for (c in 0 until width) {
                if (a[c] != b[c]) count += 1
            }
        }
        return count
    }

    /** Row indices whose content or style differs from [other]. */
    fun changedRows(other: ScreenSnapshot): List<Int> {
        if (width != other.width || height != other.height) return rows.indices.toList()
        return rows.indices.filter { rows[it] != other.rows[it] }
    }

    companion object {
        fun empty(width: Int, height: Int): ScreenSnapshot =
            ScreenSnapshot(
                width = width,
                height = height,
                rows = List(height) { List(width) { Cell.BLANK } },
                cursorRow = 0,
                cursorCol = 0,
                cursorVisible = true,
                scrollbackSize = 0,
                scrollbackClearCount = 0,
            )
    }
}
