package io.github.darkryh.dispatch.runtime

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.RenderRegionPlaceable
import io.github.darkryh.dispatch.render.RenderDiagnostics
import io.github.darkryh.dispatch.render.TerminalRenderer
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

    /**
     * Release only the frame-diff snapshot, keeping the committed scrollback shadow intact. Used by
     * idle hibernation's conservative release: drops the diff cache (rebuilt by a full repaint on
     * the next frame) without discarding history.
     */
    fun releaseDiffSnapshot() {
        renderLock.withLock {
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
                        // Entering a new screen is a declared reason to destroy history: the
                        // previous screen must not remain above the new one when the user scrolls
                        // back. A resize is not — it needs the visible screen erased so the old
                        // frame's shape leaves no residue, but the history stays.
                        clearScrollback = screenTransition,
                        eraseScreen = true,
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
                ),
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
                            if (update.eraseScreen) {
                                scrollingLines
                            } else {
                                viewportScrollingLines(scrollingLines, activeLines, height)
                            }
                        renderer.rewriteViewport(
                            scrollingLines = renderedScrollingLines,
                            activeLines = activeLines,
                            clearScrollback = update.clearScrollback,
                            eraseScreen = update.eraseScreen,
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
    /**
     * Destroy the terminal's scrollback (`ESC[3J`).
     *
     * This throws away the user's history, so it is reserved for the cases that genuinely make the
     * history obsolete: entering a different screen, and resetting the scrolling content to empty.
     */
    val clearScrollback: Boolean = false,
    /**
     * Erase the visible screen before repainting (`ESC[J`).
     *
     * Independent of [clearScrollback]. A resize needs this — the previous, differently-shaped
     * frame leaves rows the new frame never addresses — but it must not take the history with it.
     */
    val eraseScreen: Boolean = false,
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
            // NOTE: this still destroys scrollback, and it fires on ANY shrink of the scrolling
            // region — not only on a genuine content reset. That is too broad: a list filtering by
            // one row deletes the user's history just as surely as clearing a chat does.
            //
            // It is left as-is deliberately. Narrowing it by shape (e.g. "only when the new content
            // is empty") is wrong — clearing the sample chat leaves 9 header rows, not 0, and the
            // e2e suite correctly rejects that. The real fix is to let a screen or widget DECLARE
            // that it resets history, rather than inferring intent from a line count. Until that
            // policy exists, preserving the current product behaviour beats guessing at it.
            clearScrollback = true,
            eraseScreen = true,
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
