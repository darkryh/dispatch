package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.DispatchComposition
import com.ead.dispatch.runtime.composableWidget
import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalScreenTest {
    private class Lines(
        private val values: List<String>,
    ) : Measurable {
        override val modifier: Modifier = Modifier

        override fun measure(constraints: Constraints): Placeable =
            SimplePlaceable(
                width = constraints.constrainWidth(values.maxOfOrNull { it.length } ?: 0),
                height = constraints.constrainHeight(values.size),
                lines = values.take(constraints.maxHeight),
            )
    }

    @Test
    fun `footer owns rows outside bounded body`() {
        DispatchComposition().use { composition ->
            composition.setContent {
                TerminalScreen(
                    footer = { composableWidget("footer") { Lines(listOf("fixed-footer")) } },
                ) {
                    composableWidget("body") { Lines(List(20) { "body-$it" }) }
                }
            }
            val lines =
                composition.root.children
                    .single()
                    .measure(Constraints.fixed(20, 5))
                    .lines
            assertEquals(5, lines.size)
            assertEquals("fixed-footer", lines.last().trim())
        }
    }
}
