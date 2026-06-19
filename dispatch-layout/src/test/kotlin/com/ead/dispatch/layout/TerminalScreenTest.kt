package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.DispatchComposition
import com.ead.dispatch.runtime.composableWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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

    @Test
    fun `active boundary is stable when footer structure grows`() {
        fun measure(overlayLines: Int): RenderRegionPlaceable {
            val composition = DispatchComposition()
            composition.setContent {
                TerminalScreen(
                    header = { composableWidget("header") { Lines(listOf("header")) } },
                    footer = {
                        composableWidget("input") { Lines(listOf("input")) }
                        if (overlayLines > 0) {
                            composableWidget("overlay") { Lines(List(overlayLines) { "overlay-$it" }) }
                        }
                    },
                ) {
                    composableWidget("body") { Lines(List(20) { "body-$it" }) }
                }
            }
            val placeable =
                composition.root.children
                    .single()
                    .measure(Constraints.fixedWidth(80))
            composition.close()
            return assertIs<RenderRegionPlaceable>(placeable)
        }

        val withoutOverlay = measure(overlayLines = 0)
        val withOverlay = measure(overlayLines = 6)

        assertEquals(21, withoutOverlay.activeStartLine)
        assertEquals(21, withOverlay.activeStartLine)
        assertEquals(1, withoutOverlay.lines.size - withoutOverlay.activeStartLine)
        assertEquals(7, withOverlay.lines.size - withOverlay.activeStartLine)
    }
}
