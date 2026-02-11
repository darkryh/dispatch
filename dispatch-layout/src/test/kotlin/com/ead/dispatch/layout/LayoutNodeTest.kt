package com.ead.dispatch.layout

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.semantics
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.composableWidget
import com.ead.dispatch.runtime.withComposer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        val composer = Composer()
        withComposer(composer) {
            composer.startComposition()
            Column {
                composableWidget("ChildA") { StubMeasurable() }
                composableWidget("ChildB") { StubMeasurable() }
            }
            composer.endComposition()
        }

        val root = composer.getRootNode()
        assertNotNull(root)
        assertEquals("Layout", root.name)
        assertEquals(2, root.children.size)
        assertEquals(listOf("ChildA", "ChildB"), root.children.map { it.name })
        assertSame(root, root.children.first().parent)
    }

    @Test
    fun `node modifier reflects leaf measurable modifier`() {
        val composer = Composer()
        val modifier = TagModifier("leaf")

        withComposer(composer) {
            composer.startComposition()
            composableWidget("Leaf") { StubMeasurable(modifier = modifier) }
            composer.endComposition()
        }

        val root = composer.getRootNode()
        assertNotNull(root)
        assertTrue(root.modifier.any { it is TagModifier })
    }

    @Test
    fun `node exposes semantics tags from modifier`() {
        val composer = Composer()
        val modifier = Modifier.semantics("alpha").semantics("beta")

        withComposer(composer) {
            composer.startComposition()
            composableWidget("Leaf") { StubMeasurable(modifier = modifier) }
            composer.endComposition()
        }

        val root = composer.getRootNode()
        assertNotNull(root)
        assertEquals(listOf("alpha", "beta"), root.semanticsTags)
    }
}
