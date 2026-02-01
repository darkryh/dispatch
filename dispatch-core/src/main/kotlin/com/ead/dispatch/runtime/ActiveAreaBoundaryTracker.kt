package com.ead.dispatch.runtime

/**
 * Tracks active area line count changes that require a render reset.
 */
internal class ActiveAreaBoundaryTracker {
    private var lastLineCount: Int? = null

    /**
     * Return true when the active area line count changes from the last render.
     */
    fun shouldReset(activeAreaLineCount: Int): Boolean {
        val previous = lastLineCount
        lastLineCount = activeAreaLineCount
        return previous != null && previous != activeAreaLineCount
    }
}
