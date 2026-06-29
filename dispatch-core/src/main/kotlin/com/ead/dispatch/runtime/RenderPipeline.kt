package com.ead.dispatch.runtime

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.RenderRegionPlaceable
import com.ead.dispatch.render.RenderDiagnostics
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

    @Suppress("LongMethod")
    fun render(
        measurable: Measurable?,
        terminalWidth: Int,
        terminalHeight: Int,
        activeAreaHeight: Int,
        forceRewrite: Boolean,
        screenTransition: Boolean,
    ) {
        if (measurable == null) return

        renderLock.withLock {
            val startedAt = System.nanoTime()
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

            val currentFrame =
                RenderFrameSnapshot(
                    scrollingLines = scrollingLines,
                    activeLines = activeLines,
                    hasExplicitRenderRegions = placeable is RenderRegionPlaceable,
                    scrollingContentStartLine =
                        (placeable as? RenderRegionPlaceable)?.scrollingStartLine ?: 0,
                )
            val update =
                if (forceRewrite || screenTransition) {
                    RenderDecision(
                        kind = RenderKind.FULL_REWRITE,
                        scrollUpdate = ScrollUpdate.rewrite(scrollingLines),
                        confidencePercent = 100,
                        reason = if (forceRewrite) "terminal_resize" else "screen_transition",
                        clearScrollback = true,
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
                    RenderKind.NOOP -> {
                        false
                    }
                    RenderKind.ACTIVE_ONLY -> {
                        false
                    }
                    RenderKind.APPEND_ONLY -> {
                        if (update.scrollUpdate.lines.isNotEmpty()) {
                            renderer.appendScrollingContent(update.scrollUpdate.lines)
                        }
                        false
                    }
                    RenderKind.FULL_REWRITE -> {
                        // Same-screen rewrites must repaint only the visible tail. Replaying the
                        // entire committed history would push it through the viewport again when
                        // an overlay changes the active-area boundary (palette, selector, prompt).
                        val renderedScrollingLines =
                            if (update.clearScrollback) {
                                scrollingLines
                            } else {
                                viewportScrollingLines(scrollingLines, activeLines, height)
                            }
                        renderer.rewriteViewport(
                            scrollingLines = renderedScrollingLines,
                            activeLines = activeLines,
                            clearScrollback = update.clearScrollback,
                        )
                        scrollingContentTracker.sync(scrollingLines)
                        true
                    }
                }

            if (!rewriteRendered) {
                renderer.updateActiveArea(activeLines)
            }
            lastRenderedFrame = currentFrame
            recordFrameDiagnostics(
                update = update,
                forceRewrite = forceRewrite,
                screenTransition = screenTransition,
                width = width,
                height = height,
                scrollingLines = scrollingLines,
                activeLines = activeLines,
                currentFrame = currentFrame,
                startedAt = startedAt,
            )
        }
    }

    /**
     * Emit per-frame diagnostics only when enabled. Building this map (especially
     * `currentFrame.hashCode()`, which hashes the entire committed scrollback) is the most
     * expensive per-frame work on the render path, so it is skipped entirely unless
     * `DISPATCH_DIAGNOSTICS_FILE` is set. The output is a file-only sidecar and never touches the
     * terminal, so guarding it does not change emitted bytes or timing.
     */
    @Suppress("LongParameterList")
    private fun recordFrameDiagnostics(
        update: RenderDecision,
        forceRewrite: Boolean,
        screenTransition: Boolean,
        width: Int,
        height: Int,
        scrollingLines: List<String>,
        activeLines: List<String>,
        currentFrame: RenderFrameSnapshot,
        startedAt: Long,
    ) {
        if (!RenderDiagnostics.isEnabled) return
        RenderDiagnostics.record(
            event = "render_frame",
            fields =
                mapOf(
                    "kind" to update.kind,
                    "reason" to update.reason,
                    "forceRewrite" to forceRewrite,
                    "screenTransition" to screenTransition,
                    "clearScrollback" to update.clearScrollback,
                    "terminalWidth" to width,
                    "terminalHeight" to height,
                    "scrollingLines" to scrollingLines.size,
                    "visibleScrollingLines" to viewportScrollingLines(scrollingLines, activeLines, height).size,
                    "activeLines" to activeLines.size,
                    "frameHash" to currentFrame.hashCode(),
                    "durationNanos" to System.nanoTime() - startedAt,
                ),
        )
        RenderDiagnostics.recordMemoryIfDue()
    }
}

internal data class RenderFrameSnapshot(
    val scrollingLines: List<String>,
    val activeLines: List<String>,
    val hasExplicitRenderRegions: Boolean = false,
    val scrollingContentStartLine: Int = 0,
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
    val clearScrollback: Boolean = false,
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

    if (previous != null && current.scrollingLines.size < previous.scrollingLines.size) {
        return RenderDecision(
            kind = RenderKind.FULL_REWRITE,
            scrollUpdate = ScrollUpdate.rewrite(current.scrollingLines),
            confidencePercent = 100,
            reason = "scrolling_content_reset",
            clearScrollback = true,
        )
    }

    if (
        previous != null &&
        current.hasExplicitRenderRegions &&
        current.scrollingLines.size > previous.scrollingLines.size &&
        scrollUpdate.kind == ScrollUpdateKind.REWRITE
    ) {
        val previousBody = previous.scrollingLines.drop(previous.scrollingContentStartLine)
        val currentBody = current.scrollingLines.drop(current.scrollingContentStartLine)
        if (currentBody.take(previousBody.size) == previousBody) {
            return RenderDecision(
                kind = RenderKind.APPEND_ONLY,
                scrollUpdate = ScrollUpdate.append(currentBody.drop(previousBody.size)),
                confidencePercent = 95,
                reason = "structural_scrolling_growth",
            )
        }
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
