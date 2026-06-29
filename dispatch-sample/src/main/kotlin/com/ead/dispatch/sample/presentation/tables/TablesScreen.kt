@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.tables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.designsystem.ControlPanel
import com.ead.dispatch.sample.designsystem.ControlSpec
import com.ead.dispatch.sample.designsystem.PlaygroundController
import com.ead.dispatch.sample.designsystem.PlaygroundIntent
import com.ead.dispatch.sample.designsystem.PlaygroundScaffold
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.FilterableTable
import com.ead.dispatch.widget.Grid
import com.ead.dispatch.widget.GridCells
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Table
import com.ead.dispatch.widget.TableColumn
import com.ead.dispatch.widget.TableColumnWidth
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.rememberTableState
import com.github.ajalt.mordant.rendering.TextAlign

/**
 * Tables & Grid playground.
 *
 * Demonstrates: [Grid] (with both [GridCells.Fixed] and [GridCells.Adaptive] layouts), [Table] with
 * caller-defined [TableColumn]s sized via every [TableColumnWidth] strategy (Auto / Fixed / Weight),
 * and the interactive [FilterableTable] (type letters to filter; arrows move its selection). The grid
 * strategy and the first column's width strategy are both driven live from the controls pane.
 */
@Composable
fun TablesScreen(viewModel: TablesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Tables & Grid",
        subtitle = "←/→ cycles grid + column strategy · type to filter the table",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Grid cells", listOf("Fixed(3)", "Adaptive(16)"), state.gridMode),
                        ControlSpec.Cycle("Column width", listOf("Auto", "Fixed", "Weight"), state.columnWidth),
                    ),
                selected = state.selected,
            )
        },
        preview = { TablesPreview(state) },
    )
}

@Composable
private fun TablesPreview(state: TablesState) {
    val theme = LocalTheme.current

    val chosenWidth: TableColumnWidth =
        when (state.columnWidth) {
            0 -> TableColumnWidth.Auto
            1 -> TableColumnWidth.Fixed(12)
            else -> TableColumnWidth.Weight(1f)
        }

    val columns =
        listOf(
            TableColumn<Server>(header = "Name", width = chosenWidth, valueOf = { it.name }),
            TableColumn<Server>(header = "Region", valueOf = { it.region }),
            TableColumn<Server>(header = "CPU%", align = TextAlign.RIGHT, valueOf = { it.cpu.toString() }),
        )

    Column {
        Text("Grid — ${if (state.gridMode == 0) "Fixed(3)" else "Adaptive(16)"}", style = theme.muted)
        Grid(
            items = SERVERS,
            cells = if (state.gridMode == 0) GridCells.Fixed(3) else GridCells.Adaptive(minSize = 16),
        ) { server, _ ->
            Panel(title = server.name) {
                Text(server.region)
            }
        }
        Spacer(Modifier.height(1))

        Text("Table — column width ${state.columnWidth + 1}/3", style = theme.muted)
        Table(items = SERVERS, columns = columns)
        Spacer(Modifier.height(1))

        Text("FilterableTable — type to filter", style = theme.muted)
        FilterableTable(
            items = SERVERS,
            columns = columns,
            filterPredicate = { s, q -> s.name.contains(q, true) || s.region.contains(q, true) },
            onRowSelected = {},
            onExit = {},
            filterPromptStyle = theme.info,
            state = rememberTableState(),
        )
    }
}
