package com.ead.dispatch.runtime

import com.github.ajalt.mordant.terminal.Terminal
import sun.misc.Signal

internal class ResizeCoordinator(
    private val requestRecomposition: () -> Unit,
) {
    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private var sizeDirty: Boolean = true
    private var pendingResizeReset: Boolean = false
    private var resizeHandlerRegistered: Boolean = false

    fun markInitialized(
        width: Int,
        height: Int,
    ) {
        this.width = width
        this.height = height
        sizeDirty = false
    }

    fun registerSignalHandler() {
        if (resizeHandlerRegistered) return
        try {
            Signal.handle(Signal("WINCH")) {
                sizeDirty = true
                requestRecomposition()
            }
            resizeHandlerRegistered = true
        } catch (_: Throwable) {
            // No-op when signals aren't supported.
        }
    }

    fun updateIfNeeded(terminal: Terminal?) {
        if (terminal == null || !sizeDirty) return

        val size = terminal.updateSize()
        val update =
            computeTerminalSizeUpdate(
                sizeDirty = sizeDirty,
                previousWidth = width,
                previousHeight = height,
                currentWidth = size.width,
                currentHeight = size.height,
            )
        width = update.width
        height = update.height
        if (update.reset) {
            pendingResizeReset = true
        }
        sizeDirty = update.dirty
    }

    fun consumePendingReset(): Boolean {
        val pending = pendingResizeReset
        pendingResizeReset = false
        return pending
    }
}

