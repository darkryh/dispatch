@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.widget.Grid
import io.github.darkryh.dispatch.widget.GridCells
import io.github.darkryh.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle

data class CountTile(
    val label: String,
    val count: Int,
)

@Composable
fun CountTileGrid(
    items: List<CountTile>,
    modifier: Modifier = Modifier,
    cells: GridCells = GridCells.Adaptive(minSize = 18),
    gap: Int = 2,
    leftPadding: Int = 2,
    styleForCount: (Int) -> TextStyle = { TextColors.white },
) {
    Grid(
        items = items,
        modifier = modifier,
        cells = cells,
        gap = gap,
        leftPadding = leftPadding,
    ) { item, cellWidth ->
        Row {
            Text(buildTile(item, cellWidth), style = styleForCount(item.count))
        }
    }
}

private fun buildTile(
    item: CountTile,
    width: Int,
): String {
    val countText = item.count.toString()
    val maxLabelLength = (width - countText.length - 4).coerceAtLeast(1)
    val label =
        if (item.label.length > maxLabelLength) {
            val cut = (maxLabelLength - 3).coerceAtLeast(1)
            item.label.take(cut) + "..."
        } else {
            item.label
        }
    val raw = "[$label] $countText"
    return if (raw.length >= width) raw.take(width) else raw.padEnd(width)
}
