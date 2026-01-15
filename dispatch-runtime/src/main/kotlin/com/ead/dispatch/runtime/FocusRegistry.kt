package com.ead.dispatch.runtime

import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.setValue
import com.github.ajalt.mordant.input.KeyboardEvent

/**
 * Tracks focusable elements within the current composition.
 */
class FocusRegistry {
    private val order = mutableListOf<Any>()
    private var focusedToken: Any? by mutableStateOf(null)
    private var lastEvent: KeyboardEvent? = null

    fun register(token: Any): () -> Unit {
        if (!order.contains(token)) {
            order.add(token)
            if (focusedToken == null) {
                focusedToken = token
            }
        }
        return { unregister(token) }
    }

    fun unregister(token: Any) {
        order.remove(token)
        if (focusedToken === token) {
            focusedToken = order.firstOrNull()
        }
    }

    fun isFocused(token: Any): Boolean = focusedToken === token

    fun claimEvent(event: KeyboardEvent): Boolean {
        if (event === lastEvent) return false
        lastEvent = event
        return true
    }

    fun focusNext() {
        if (order.isEmpty()) return
        val currentIndex = order.indexOf(focusedToken)
        val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % order.size
        focusedToken = order[nextIndex]
    }

    fun focusPrevious() {
        if (order.isEmpty()) return
        val currentIndex = order.indexOf(focusedToken)
        val previousIndex = if (currentIndex < 0) 0 else (currentIndex - 1 + order.size) % order.size
        focusedToken = order[previousIndex]
    }
}
