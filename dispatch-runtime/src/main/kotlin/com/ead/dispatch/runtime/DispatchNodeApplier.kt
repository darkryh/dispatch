package com.ead.dispatch.runtime

import androidx.compose.runtime.AbstractApplier
import com.ead.dispatch.layout.LayoutNode

/** Applies Compose tree mutations to Dispatch's retained terminal layout tree. */
class DispatchNodeApplier(
    root: LayoutNode,
    private val onEndChangesCallback: () -> Unit = {},
) : AbstractApplier<LayoutNode>(root) {
    override fun insertTopDown(index: Int, instance: LayoutNode) {
        current.insertChild(index, instance)
    }

    override fun insertBottomUp(index: Int, instance: LayoutNode) = Unit

    override fun remove(index: Int, count: Int) {
        current.removeChildren(index, count)
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.moveChildren(from, to, count)
    }

    override fun onClear() {
        root.clearChildren()
    }

    override fun onEndChanges() {
        onEndChangesCallback()
    }
}
