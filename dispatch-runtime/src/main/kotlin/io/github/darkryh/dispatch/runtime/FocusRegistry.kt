package io.github.darkryh.dispatch.runtime

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.ajalt.mordant.input.KeyboardEvent
import io.github.darkryh.dispatch.layout.LayoutNode
import io.github.darkryh.dispatch.modifier.FocusTargetModifier

/**
 * Tracks focusable elements within the current composition.
 *
 * Focus targets are discovered via [sync], which scans the layout node tree for
 * [io.github.darkryh.dispatch.modifier.FocusTargetModifier] entries. If a modifier does not
 * specify a token, the owning [io.github.darkryh.dispatch.layout.LayoutNode] is used as the token.
 */
class FocusRegistry {
    private val order = mutableListOf<Any>()

    // Reused across syncs so the per-frame focus walk allocates nothing; cleared at the start of
    // each sync(). Safe because sync() runs single-threaded on the render path.
    private val seenScratch = HashSet<Any>()
    private var focusedToken: Any? by mutableStateOf(null)
    private var lastEvent: KeyboardEvent? = null

    /**
     * Synchronize focus order with the current layout tree.
     *
     * Call this after a composition completes (e.g. after `composer.getRootNode()`)
     * so focus order matches the rendered node tree.
     */
    fun sync(root: LayoutNode?) {
        order.clear()
        seenScratch.clear()
        if (root != null) {
            // Collect directly into `order` (nothing reads it mid-walk) and de-dup via a reused
            // HashSet, so a frame with an unchanged tree allocates nothing here.
            collectFocusTargets(root, order, seenScratch)
        }
        focusedToken =
            when {
                order.isEmpty() -> {
                    lastEvent = null
                    null
                }
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

    private fun collectFocusTargets(
        node: LayoutNode,
        target: MutableList<Any>,
        seen: HashSet<Any>,
    ) {
        // foldIn visits elements outside-to-inside (identical order to the previous allOf()) but
        // without allocating a list per node.
        node.modifier.foldIn(Unit) { _, element ->
            if (element is FocusTargetModifier) {
                val token = element.token ?: node
                if (seen.add(token)) {
                    target.add(token)
                }
            }
        }
        val children = node.children
        for (index in children.indices) {
            collectFocusTargets(children[index], target, seen)
        }
    }
}
