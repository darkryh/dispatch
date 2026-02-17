package com.ead.dispatch.runtime

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.render.TerminalRenderer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class RenderPipeline(
    private val renderer: TerminalRenderer,
) {
    private val renderLock = ReentrantLock()
    private val scrollingContentTracker = ScrollingContentTracker()
    private var lastRenderedFrame: RenderFrameSnapshot? = null

    fun reset() {
        renderLock.withLock {
            scrollingContentTracker.reset()
            lastRenderedFrame = null
        }
    }

    fun render(
        measurable: Measurable?,
        terminalWidth: Int,
        terminalHeight: Int,
        activeAreaHeight: Int,
        forceRewrite: Boolean,
    ) {
        if (measurable == null) return

        renderLock.withLock {
            val width = terminalWidth.coerceAtLeast(40)
            val height = terminalHeight.coerceAtLeast(10)
            val effectiveActiveAreaHeight = minOf(activeAreaHeight, height)

            val constraints =
                Constraints(
                    minWidth = width,
                    maxWidth = width,
                    minHeight = 0,
                    maxHeight = Int.MAX_VALUE,
                )

            val placeable = measurable.measure(constraints)
            val (scrollingLines, activeLines) =
                splitContentForRendering(
                    placeable = placeable,
                    activeAreaHeight = effectiveActiveAreaHeight,
                    committedLineCount = scrollingContentTracker.committedLineCount,
                )

            val currentFrame = RenderFrameSnapshot(scrollingLines = scrollingLines, activeLines = activeLines)
            val update =
                if (forceRewrite) {
                    RenderDecision(
                        kind = RenderKind.FULL_REWRITE,
                        scrollUpdate = ScrollUpdate.rewrite(scrollingLines),
                        confidencePercent = 100,
                        reason = "terminal_resize",
                    )
                } else {
                    classifyRenderDecision(
                        previous = lastRenderedFrame,
                        current = currentFrame,
                        scrollUpdate = scrollingContentTracker.consume(scrollingLines),
                    )
                }
            RenderDecisionTelemetry.record(update)

            renderer.markVisibleContentHeight(
                viewportLineCount(
                    scrollingLines = scrollingLines,
                    activeLines = activeLines,
                    terminalHeight = height,
                )
            )

            val rewriteRendered =
                when (update.kind) {
                    RenderKind.NOOP -> false
                    RenderKind.ACTIVE_ONLY -> false
                    RenderKind.APPEND_ONLY -> {
                        if (update.scrollUpdate.lines.isNotEmpty()) {
                            renderer.appendScrollingContent(update.scrollUpdate.lines)
                        }
                        false
                    }
                    RenderKind.FULL_REWRITE -> {
                        // Hard rewrite is reserved for explicit reset flows (resize/clear).
                        // Transition rewrites still use the same path today for deterministic cleanup.
                        renderer.rewriteViewport(
                            scrollingLines = scrollingLines,
                            activeLines = activeLines,
                            clearScrollback = true,
                        )
                        scrollingContentTracker.sync(scrollingLines)
                        true
                    }
                }

            if (!rewriteRendered) {
                renderer.updateActiveArea(activeLines)
            }
            lastRenderedFrame = currentFrame
        }
    }
}

internal data class RenderFrameSnapshot(
    val scrollingLines: List<String>,
    val activeLines: List<String>,
)

internal enum class RenderKind {
    NOOP,
    ACTIVE_ONLY,
    APPEND_ONLY,
    FULL_REWRITE,
}

internal data class RenderDecision(
    val kind: RenderKind,
    val scrollUpdate: ScrollUpdate,
    val confidencePercent: Int,
    val reason: String,
)

internal fun classifyRenderDecision(
    previous: RenderFrameSnapshot?,
    current: RenderFrameSnapshot,
    scrollUpdate: ScrollUpdate,
): RenderDecision {
    if (previous != null && previous == current) {
        return RenderDecision(
            kind = RenderKind.NOOP,
            scrollUpdate = ScrollUpdate.none(),
            confidencePercent = 100,
            reason = "unchanged_frame",
        )
    }

    if (scrollUpdate.kind == ScrollUpdateKind.REWRITE) {
        return RenderDecision(
            kind = RenderKind.FULL_REWRITE,
            scrollUpdate = scrollUpdate,
            confidencePercent = 100,
            reason = "non_prefix_scrolling_change",
        )
    }

    if (scrollUpdate.kind == ScrollUpdateKind.APPEND) {
        if (RenderTransitionPolicy.shouldPromoteAppendToRewrite(previous, current, scrollUpdate)) {
            return RenderDecision(
                kind = RenderKind.FULL_REWRITE,
                scrollUpdate = ScrollUpdate.rewrite(current.scrollingLines),
                confidencePercent = 100,
                reason = "active_to_scrolling_boundary_shift",
            )
        }
        return RenderDecision(
            kind = RenderKind.APPEND_ONLY,
            scrollUpdate = scrollUpdate,
            confidencePercent = 95,
            reason = "append_only_scrolling_change",
        )
    }

    return if (previous == null || previous.activeLines != current.activeLines) {
        RenderDecision(
            kind = RenderKind.ACTIVE_ONLY,
            scrollUpdate = ScrollUpdate.none(),
            confidencePercent = 95,
            reason = "active_area_only_change",
        )
    } else {
        RenderDecision(
            kind = RenderKind.NOOP,
            scrollUpdate = ScrollUpdate.none(),
            confidencePercent = 100,
            reason = "no_effective_change",
        )
    }
}
