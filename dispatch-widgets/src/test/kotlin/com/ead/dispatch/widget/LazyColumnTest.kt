package com.ead.dispatch.widget

import androidx.compose.runtime.CompositionLocalProvider
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.runtime.DispatchComposition
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LazyColumnTest {
    @Test
    fun `renders items in order`() {
        val lines =
            renderLines(width = 40) {
                LazyColumn {
                    item { Text("First") }
                    item { Text("Second") }
                }
            }

        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("First"))
        assertTrue(lines[1].contains("Second"))
    }

    @Test
    fun `composes only viewport window for large lists`() {
        var compositions = 0
        renderLines(width = 40, height = 5) {
            LazyColumn {
                items((0 until 1_000).toList(), key = { it }) { index ->
                    compositions++
                    Text("Item $index")
                }
            }
        }

        assertTrue(compositions in 1..20, "expected a viewport window, composed $compositions items")
    }

    @Test
    fun `stick to end renders newest records in bounded viewport`() {
        val terminal =
            Terminal(
                ansiLevel = AnsiLevel.NONE,
                width = 20,
                height = 3,
                interactive = false,
            )
        val lines =
            DispatchComposition().use { composition ->
                composition.setContent {
                    CompositionLocalProvider(
                        LocalTerminal provides terminal,
                        LocalTerminalWidth provides 20,
                        LocalTerminalHeight provides 3,
                    ) {
                        LazyColumn(stickToEnd = true) {
                            items((0 until 10).toList(), key = { it }) { index ->
                                Text("Record $index")
                            }
                        }
                    }
                }
                composition.root.children
                    .single()
                    .measure(Constraints.fixed(20, 3))
                    .lines
            }

        assertEquals(listOf("Record 7", "Record 8", "Record 9"), lines.map { it.trimEnd() })
    }
}
