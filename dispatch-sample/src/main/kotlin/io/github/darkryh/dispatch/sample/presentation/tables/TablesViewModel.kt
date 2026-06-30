package io.github.darkryh.dispatch.sample.presentation.tables

import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.wrapIndex
import io.github.darkryh.dispatch.viewmodel.MviViewModel

/** A single demo row rendered by the grid and the two tables. */
data class Server(
    val name: String,
    val region: String,
    val cpu: Int,
)

/** Sample data shared by every widget in the Tables & Grid playground. */
val SERVERS: List<Server> =
    listOf(
        Server("alpha", "us-east-1", 18),
        Server("bravo", "us-west-2", 64),
        Server("charlie", "eu-central-1", 7),
        Server("delta", "eu-west-1", 91),
        Server("echo", "ap-south-1", 42),
        Server("foxtrot", "sa-east-1", 33),
    )

/**
 * State of the Tables & Grid playground. [selected] is the highlighted control row; [gridMode] picks
 * the [io.github.darkryh.dispatch.widget.GridCells] strategy and [columnWidth] picks the first column's
 * [io.github.darkryh.dispatch.widget.TableColumnWidth] strategy.
 */
data class TablesState(
    val selected: Int = 0,
    val gridMode: Int = 0,
    val columnWidth: Int = 0,
)

/** Number of editable control rows; selection wraps within this count. */
const val TABLES_CONTROL_COUNT = 2

/**
 * Drives the Tables & Grid playground. Demonstrates [MviViewModel] with the shared
 * [PlaygroundIntent]: ↑/↓ moves [TablesState.selected] and ←/→ cycles the selected strategy. The
 * preview's `FilterableTable` owns letter keys directly, so this view-model never sees them; Toggle
 * is a no-op because the playground has no boolean controls.
 */
class TablesViewModel : MviViewModel<TablesState, PlaygroundIntent>(TablesState()) {
    override suspend fun handleIntent(intent: PlaygroundIntent) {
        when (intent) {
            is PlaygroundIntent.Select ->
                updateState { it.copy(selected = wrapIndex(it.selected, intent.delta, TABLES_CONTROL_COUNT)) }

            is PlaygroundIntent.Change ->
                updateState { state ->
                    when (state.selected) {
                        0 -> state.copy(gridMode = wrapIndex(state.gridMode, intent.delta, 2))
                        1 -> state.copy(columnWidth = wrapIndex(state.columnWidth, intent.delta, 3))
                        else -> state
                    }
                }

            PlaygroundIntent.Toggle -> Unit
        }
    }
}
