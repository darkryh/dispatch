package io.github.darkryh.dispatch.sample.presentation.home

import io.github.darkryh.dispatch.sample.navigation.CatalogDestination
import io.github.darkryh.dispatch.viewmodel.MviViewModel

/** Which way the launcher cursor should move. */
enum class MoveDirection { UP, DOWN, LEFT, RIGHT }

/**
 * State of the Home launcher grid.
 *
 * @param cursor flat index (into [CatalogDestination.entries]) of the highlighted card.
 * @param columns how many columns the grid currently lays out in (derived from terminal width).
 */
data class HomeState(
    val cursor: Int = 0,
    val columns: Int = CatalogDestination.COLUMNS,
)

/** Intents the Home screen can dispatch. */
sealed interface HomeIntent {
    /** Move the cursor one card in [direction], clamped at the grid edges. */
    data class Move(
        val direction: MoveDirection,
    ) : HomeIntent

    /** Tell the view-model how many columns the current terminal width affords. */
    data class Init(
        val columns: Int,
    ) : HomeIntent
}

/**
 * Drives the Home launcher grid. A deliberate showcase of [MviViewModel]: the screen only ever
 * sends intents and renders [state]; all 2-D cursor arithmetic lives here and is unit-tested in
 * isolation (no Compose, no terminal).
 */
class HomeViewModel : MviViewModel<HomeState, HomeIntent>(HomeState()) {
    override suspend fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Init ->
                updateState { state ->
                    val columns = intent.columns.coerceAtLeast(1)
                    state.copy(columns = columns, cursor = state.cursor.coerceIn(0, lastIndex))
                }

            is HomeIntent.Move ->
                updateState { state ->
                    state.copy(cursor = move(state.cursor, state.columns, intent.direction))
                }
        }
    }

    private val lastIndex: Int get() = CatalogDestination.entries.size - 1
}

/**
 * Pure grid arithmetic, extracted so it is trivial to test. Movement is clamped at the edges: you
 * never wrap around, and you never land past the last card on a ragged final row.
 */
internal fun move(
    cursor: Int,
    columns: Int,
    direction: MoveDirection,
): Int {
    val count = CatalogDestination.entries.size
    val cols = columns.coerceAtLeast(1)
    val col = cursor % cols
    val row = cursor / cols
    val next =
        when (direction) {
            MoveDirection.LEFT -> if (col > 0) cursor - 1 else cursor
            MoveDirection.RIGHT -> if (col < cols - 1 && cursor + 1 < count) cursor + 1 else cursor
            MoveDirection.UP -> if (row > 0) cursor - cols else cursor
            MoveDirection.DOWN -> if (cursor + cols < count) cursor + cols else cursor
        }
    return next.coerceIn(0, count - 1)
}
