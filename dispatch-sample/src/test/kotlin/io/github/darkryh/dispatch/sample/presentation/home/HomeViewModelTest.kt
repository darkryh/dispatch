package io.github.darkryh.dispatch.sample.presentation.home

import io.github.darkryh.dispatch.sample.navigation.CatalogDestination
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for the pure 2-D launcher-grid arithmetic ([move]). No Compose, no terminal — just the
 * cursor math the Home screen relies on. The grid has [CatalogDestination] entries laid out in
 * [CatalogDestination.COLUMNS] columns.
 */
class HomeViewModelTest {
    private val count = CatalogDestination.entries.size
    private val columns = CatalogDestination.COLUMNS

    @Test
    fun `right moves within a row`() {
        assertEquals(1, move(cursor = 0, columns = columns, direction = MoveDirection.RIGHT))
    }

    @Test
    fun `right clamps at the end of a row`() {
        val lastColInRow0 = columns - 1
        assertEquals(lastColInRow0, move(cursor = lastColInRow0, columns = columns, direction = MoveDirection.RIGHT))
    }

    @Test
    fun `left clamps at the start of a row`() {
        assertEquals(0, move(cursor = 0, columns = columns, direction = MoveDirection.LEFT))
    }

    @Test
    fun `down adds one row`() {
        assertEquals(columns, move(cursor = 0, columns = columns, direction = MoveDirection.DOWN))
    }

    @Test
    fun `up clamps on the first row`() {
        assertEquals(0, move(cursor = 0, columns = columns, direction = MoveDirection.UP))
    }

    @Test
    fun `down clamps when there is no row below`() {
        val last = count - 1
        assertEquals(last, move(cursor = last, columns = columns, direction = MoveDirection.DOWN))
    }

    @Test
    fun `result always stays in bounds`() {
        for (cursor in 0 until count) {
            for (direction in MoveDirection.entries) {
                val next = move(cursor, columns, direction)
                assertEquals(true, next in 0 until count, "out of bounds from $cursor going $direction -> $next")
            }
        }
    }
}
