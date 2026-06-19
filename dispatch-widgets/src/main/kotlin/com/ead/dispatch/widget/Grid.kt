package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LocalTerminalWidth

/**
 * A simple adaptive grid that lays out items in rows based on available width.
 */
@Composable
fun <T> Grid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cells: GridCells = GridCells.Adaptive(minSize = 18),
    gap: Int = 2,
    leftPadding: Int = 2,
    enforceCellWidth: Boolean = true,
    content: @Composable (item: T, cellWidth: Int) -> Unit,
) {
    if (items.isEmpty()) return
    val terminalWidth = LocalTerminalWidth.current
    val layout =
        GridLayout(
            items = items,
            cells = cells,
            gap = gap,
            leftPadding = leftPadding,
            terminalWidth = terminalWidth,
        )

    Column(modifier = modifier.fillMaxWidth()) {
        layout.rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (leftPadding > 0) {
                    Spacer(Modifier.width(leftPadding))
                }
                row.forEachIndexed { index, item ->
                    if (enforceCellWidth) {
                        Column(modifier = Modifier.width(layout.cellWidth)) {
                            content(item, layout.cellWidth)
                        }
                    } else {
                        content(item, layout.cellWidth)
                    }
                    if (index != row.lastIndex && gap > 0) {
                        Spacer(Modifier.width(gap))
                    }
                }
            }
        }
    }
}

sealed class GridCells {
    data class Fixed(
        val count: Int,
    ) : GridCells()

    data class Adaptive(
        val minSize: Int,
    ) : GridCells()
}

internal data class GridLayout<T>(
    val rows: List<List<T>>,
    val cellWidth: Int,
) {
    constructor(
        items: List<T>,
        cells: GridCells,
        gap: Int,
        leftPadding: Int,
        terminalWidth: Int,
    ) : this(
        result = buildGridRows(items, cells, gap, leftPadding, terminalWidth),
    )

    private constructor(result: GridResult<T>) : this(
        rows = result.rows,
        cellWidth = result.cellWidth,
    )
}

internal data class GridResult<T>(
    val rows: List<List<T>>,
    val cellWidth: Int,
)

internal fun <T> buildGridRows(
    items: List<T>,
    cells: GridCells,
    gap: Int,
    leftPadding: Int,
    terminalWidth: Int = 80,
): GridResult<T> {
    val available = (terminalWidth - leftPadding).coerceAtLeast(1)
    val (perRow, cellWidth) =
        when (cells) {
            is GridCells.Fixed -> {
                val columns = cells.count.coerceAtLeast(1)
                val totalGap = gap * (columns - 1)
                val widthForCells = (available - totalGap).coerceAtLeast(columns)
                val fixedWidth = (widthForCells / columns).coerceAtLeast(1)
                columns to fixedWidth
            }
            is GridCells.Adaptive -> {
                val minCellWidth = cells.minSize.coerceAtLeast(1)
                val computedPerRow = ((available + gap) / (minCellWidth + gap)).coerceAtLeast(1)
                val totalGap = gap * (computedPerRow - 1)
                val widthForCells = (available - totalGap).coerceAtLeast(computedPerRow)
                val adaptiveWidth = (widthForCells / computedPerRow).coerceAtLeast(1)
                computedPerRow to adaptiveWidth
            }
        }

    val rows = mutableListOf<List<T>>()
    var index = 0
    while (index < items.size) {
        val slice = items.drop(index).take(perRow)
        rows.add(slice)
        index += perRow
    }

    return GridResult(rows = rows, cellWidth = cellWidth)
}
