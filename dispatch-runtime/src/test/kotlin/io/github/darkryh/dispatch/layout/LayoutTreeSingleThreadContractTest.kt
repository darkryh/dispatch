package io.github.darkryh.dispatch.layout

import java.util.ConcurrentModificationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Documents why the runtime confines every layout-tree mutation and every layout-tree walk to one
 * thread — and what happens the moment it does not.
 *
 * [LayoutNode.children] is a read-only *view* over the backing list, deliberately: creating it once
 * keeps per-frame iteration (measure, focus collection) allocation-free. What it is not is a
 * snapshot. Iterating it while anything mutates the node fails fast, which is the right behaviour —
 * it is how the shutdown race surfaced at all instead of silently rendering a half-torn-down tree.
 *
 * The two mutators in practice are the recomposer's applier (`DispatchNodeApplier.remove`/
 * `onClear`) and `Composition.dispose()`. Both must therefore run on the UI dispatcher, and nothing
 * may dispose a composition while a frame is still in flight.
 */
class LayoutTreeSingleThreadContractTest {
    @Test
    fun `clearing children under a walk fails fast - this is what dispose does mid-frame`() {
        val root = LayoutNode("root")
        repeat(4) { root.addChild(LayoutNode("child-$it")) }

        val walk = root.children.iterator()
        walk.next()

        // Exactly what Composition.dispose() -> AbstractApplier.clear() -> onClear() does.
        root.clearChildren()

        assertFailsWith<ConcurrentModificationException>(
            "a frame that is mid-walk must not be able to read a tree someone else is clearing",
        ) { walk.next() }
    }

    @Test
    fun `removing children under a walk fails fast - this is what the applier does`() {
        val root = LayoutNode("root")
        repeat(4) { root.addChild(LayoutNode("child-$it")) }

        val walk = root.children.iterator()
        walk.next()

        // AbstractApplier.remove(index, count) during recomposition.
        root.removeChildren(index = 0, count = 2)

        assertFailsWith<ConcurrentModificationException> { walk.next() }
    }

    @Test
    fun `the children view reflects the backing list rather than copying it`() {
        val root = LayoutNode("root")
        val children = root.children
        assertEquals(0, children.size)

        root.addChild(LayoutNode("added-later"))

        assertEquals(1, children.size, "the view is live — that is why iterating it needs the contract")
    }
}
