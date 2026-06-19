package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.semantics
import com.ead.dispatch.runtime.DispatchComposition
import com.ead.dispatch.runtime.composableWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LayoutNodeTest {
    private data class TagModifier(
        val tag: String,
    ) : Modifier.Element

    private class StubMeasurable(
        override val modifier: Modifier = Modifier,
        private val width: Int = 1,
        private val height: Int = 1,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable =
            SimplePlaceable(width = width, height = height, lines = List(height) { " ".repeat(width) })
    }

    @Test
    fun `node tree preserves parent-child relationships`() {
        DispatchComposition().use { composition ->
            composition.setContent {
                Column {
                    composableWidget("ChildA") { StubMeasurable() }
                    composableWidget("ChildB") { StubMeasurable() }
                }
            }
            val root = composition.root.children.single()
            assertEquals("Layout", root.name)
            assertEquals(2, root.children.size)
            assertEquals(listOf("ChildA", "ChildB"), root.children.map { it.name })
            assertSame(root, root.children.first().parent)
        }
    }

    @Test
    fun `node modifier reflects leaf measurable modifier`() {
        val modifier = TagModifier("leaf")
        DispatchComposition().use { composition ->
            composition.setContent { composableWidget("Leaf") { StubMeasurable(modifier = modifier) } }
            assertTrue(
                composition.root.children
                    .single()
                    .modifier
                    .any { it is TagModifier },
            )
        }
    }

    @Test
    fun `node exposes semantics tags from modifier`() {
        val modifier = Modifier.semantics("alpha").semantics("beta")
        DispatchComposition().use { composition ->
            composition.setContent { composableWidget("Leaf") { StubMeasurable(modifier = modifier) } }
            assertEquals(
                listOf("alpha", "beta"),
                composition.root.children
                    .single()
                    .semanticsTags,
            )
        }
    }
}
