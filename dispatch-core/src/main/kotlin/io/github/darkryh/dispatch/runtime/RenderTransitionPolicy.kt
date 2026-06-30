package io.github.darkryh.dispatch.runtime

/**
 * Transition-specific policy helpers for render classification.
 */
internal object RenderTransitionPolicy {
    /**
     * Promote append updates to rewrite when appended lines are actually the prefix of the
     * previous active area. This indicates a boundary transfer between active and scrolling
     * regions rather than true history growth.
     */
    fun shouldPromoteAppendToRewrite(
        previous: RenderFrameSnapshot?,
        current: RenderFrameSnapshot,
        scrollUpdate: ScrollUpdate,
    ): Boolean {
        if (previous == null || scrollUpdate.lines.isEmpty()) return false
        if (previous.activeLines == current.activeLines) return false

        val appended = scrollUpdate.lines
        if (appended.size > previous.activeLines.size) return false

        return previous.activeLines.take(appended.size) == appended
    }
}
