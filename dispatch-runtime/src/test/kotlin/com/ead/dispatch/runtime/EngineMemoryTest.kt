package com.ead.dispatch.runtime

import androidx.compose.runtime.DisposableEffect
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.focusable
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates the engine's memory-management invariants: compositions release their effects and
 * roots on close (and close() does not hang on the recomposer), and the focus registry does not
 * retain node references once the tree is gone.
 */
class EngineMemoryTest {
    private class StubMeasurable(
        override val modifier: Modifier = Modifier,
    ) : Measurable {
        override fun measure(constraints: Constraints): Placeable = SimplePlaceable.Empty
    }

    @Test
    fun `composition close runs DisposableEffect cleanup`() {
        val disposed = AtomicBoolean(false)
        val composition = DispatchComposition()
        composition.setContent {
            DisposableEffect(Unit) {
                onDispose { disposed.set(true) }
            }
        }

        // close() disposes the composition and cancelAndJoin()s the recomposer job; if the join
        // were missing/raced this could hang or skip disposal. The cleanup must have run.
        composition.close()

        assertTrue(disposed.get(), "DisposableEffect cleanup must run when the composition closes")
    }

    @Test
    fun `composition root is collectable after close`() {
        var lastRootRef: WeakReference<LayoutNode>? = null
        repeat(25) {
            val composition = DispatchComposition()
            composition.setContent {
                DisposableEffect(Unit) { onDispose { } }
            }
            lastRootRef = WeakReference(composition.root)
            composition.close()
        }

        val ref = lastRootRef!!

        for (i in 0 until 10) {
            System.gc()
            Thread.sleep(20)
            if (ref.get() == null) break
        }
        assertNull(ref.get(), "a closed composition's root must not be retained (no leak across cycles)")
    }

    @Test
    fun `focus registry clears focus order on empty sync`() {
        val focusRegistry = FocusRegistry()
        val token = Any()
        val root = LayoutNode("Root").apply { setDelegate(StubMeasurable()) }
        val focusNode = LayoutNode("Focusable").apply {
            setDelegate(StubMeasurable(modifier = Modifier.focusable(token)))
        }
        root.addChild(focusNode)

        focusRegistry.sync(root)
        assertTrue(focusRegistry.isFocused(token), "token should be focused after sync")

        // Syncing an empty tree must drop the focus order entirely -- no retained tokens.
        focusRegistry.sync(null)
        assertFalse(focusRegistry.isFocused(token), "focus must clear when the tree is gone")
        focusRegistry.focusNext()
        assertFalse(focusRegistry.isFocused(token), "focusNext must be a no-op with an empty order")
    }

    @Test
    fun `focus registry does not retain node after empty sync`() {
        val focusRegistry = FocusRegistry()
        var root: LayoutNode? = LayoutNode("Root").apply { setDelegate(StubMeasurable()) }
        var focusNode: LayoutNode? = LayoutNode("Focusable").apply {
            setDelegate(StubMeasurable(modifier = Modifier.focusable()))
        }
        root!!.addChild(focusNode!!)

        focusRegistry.sync(root)
        assertTrue(focusRegistry.isFocused(focusNode), "node should be focused after sync")
        val ref = WeakReference(focusNode)

        // Re-sync empty and drop the tree: the registry must hold no reference to the node.
        focusRegistry.sync(null)
        root = null
        focusNode = null

        for (i in 0 until 10) {
            System.gc()
            Thread.sleep(20)
            if (ref.get() == null) break
        }
        assertNull(ref.get(), "focusable node must be collectable after the registry syncs empty")
    }
}
