package com.ead.dispatch.runtime

import com.ead.dispatch.layout.LayoutNode
import com.ead.dispatch.modifier.FocusTargetModifier
import com.ead.dispatch.modifier.allOf
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.setValue
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Tracks focusable elements within the current composition.
 *
 * Focus targets are discovered via [sync], which scans the layout node tree for
 * [com.ead.dispatch.modifier.FocusTargetModifier] entries. If a modifier does not
 * specify a token, the owning [com.ead.dispatch.layout.LayoutNode] is used as the token.
 */
class FocusRegistry {
    private val order = mutableListOf<Any>()
    private var focusedToken: Any? by mutableStateOf(null)
    private var lastEvent: KeyboardEvent? = null

    /**
     * Synchronize focus order with the current layout tree.
     *
     * Call this after a composition completes (e.g. after `composer.getRootNode()`)
     * so focus order matches the rendered node tree.
     */
    fun sync(root: LayoutNode?) {
        val newOrder = mutableListOf<Any>()
        if (root != null) {
            collectFocusTargets(root, newOrder)
        }
        order.clear()
        order.addAll(newOrder)
        focusedToken = when {
            order.isEmpty() -> null
            focusedToken != null && order.any { it == focusedToken } -> focusedToken
            else -> order.first()
        }
    }

    fun isFocused(token: Any): Boolean = focusedToken == token

    fun claimEvent(event: KeyboardEvent): Boolean {
        if (event === lastEvent) return false
        lastEvent = event
        return true
    }

    /**
     * Move focus to the next focusable token in traversal order.
     */
    fun focusNext() {
        if (order.isEmpty()) return
        val currentIndex = order.indexOf(focusedToken)
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % order.size
        focusedToken = order[nextIndex]
    }

    /**
     * Move focus to the previous focusable token in traversal order.
     */
    fun focusPrevious() {
        if (order.isEmpty()) return
        val currentIndex = order.indexOf(focusedToken)
        val previousIndex = if (currentIndex < 0) 0 else (currentIndex - 1 + order.size) % order.size
        focusedToken = order[previousIndex]
    }

    private fun collectFocusTargets(node: LayoutNode, target: MutableList<Any>) {
        val modifiers = node.modifier.allOf<FocusTargetModifier>()
        for (modifier in modifiers) {
            val token = modifier.token ?: node
            if (!target.contains(token)) {
                target.add(token)
            }
        }
        node.children.forEach { child ->
            collectFocusTargets(child, target)
        }
    }
}
