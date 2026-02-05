package com.ead.dispatch.runtime

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.focusable
import kotlin.test.Test
import kotlin.test.assertTrue

class FocusRegistryTest {
    private class StubMeasurable(
        override val modifier: Modifier = Modifier,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable = SimplePlaceable.Empty
    }

    @Test
    fun `sync builds focus order from layout tree`() {
        val focusRegistry = FocusRegistry()

        val root = LayoutNode("Root").apply { setDelegate(StubMeasurable()) }
        val tokenA = Any()
        val tokenB = Any()

        val nodeA = LayoutNode("A").apply { setDelegate(StubMeasurable(modifier = Modifier.focusable(tokenA))) }
        val nodeA1 = LayoutNode("A1").apply { setDelegate(StubMeasurable(modifier = Modifier.focusable())) }
        val nodeB = LayoutNode("B").apply { setDelegate(StubMeasurable(modifier = Modifier.focusable(tokenB))) }

        root.addChild(nodeA)
        nodeA.addChild(nodeA1)
        root.addChild(nodeB)

        focusRegistry.sync(root)
        assertTrue(focusRegistry.isFocused(tokenA))

        focusRegistry.focusNext()
        assertTrue(focusRegistry.isFocused(nodeA1))

        focusRegistry.focusNext()
        assertTrue(focusRegistry.isFocused(tokenB))
    }

    @Test
    fun `sync preserves focus when token remains`() {
        val focusRegistry = FocusRegistry()

        val root = LayoutNode("Root").apply { setDelegate(StubMeasurable()) }
        val tokenA = Any()
        val tokenB = Any()
        val nodeA = LayoutNode("A").apply { setDelegate(StubMeasurable(modifier = Modifier.focusable(tokenA))) }
        val nodeB = LayoutNode("B").apply { setDelegate(StubMeasurable(modifier = Modifier.focusable(tokenB))) }
        root.addChild(nodeA)
        root.addChild(nodeB)

        focusRegistry.sync(root)
        focusRegistry.focusNext()
        assertTrue(focusRegistry.isFocused(tokenB))

        focusRegistry.sync(root)
        assertTrue(focusRegistry.isFocused(tokenB))
    }
}
