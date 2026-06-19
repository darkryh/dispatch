package com.ead.dispatch.runtime

import com.ead.dispatch.render.RenderDiagnostics
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight render-decision counters for debugging transition behavior.
 *
 * Enable logging with `-Ddispatch.debug.render.decisions=true`.
 */
internal object RenderDecisionTelemetry {
    private val noopCount = AtomicInteger(0)
    private val activeOnlyCount = AtomicInteger(0)
    private val appendOnlyCount = AtomicInteger(0)
    private val fullRewriteCount = AtomicInteger(0)

    fun record(decision: RenderDecision) {
        when (decision.kind) {
            RenderKind.NOOP -> noopCount.incrementAndGet()
            RenderKind.ACTIVE_ONLY -> activeOnlyCount.incrementAndGet()
            RenderKind.APPEND_ONLY -> appendOnlyCount.incrementAndGet()
            RenderKind.FULL_REWRITE -> fullRewriteCount.incrementAndGet()
        }

        RenderDiagnostics.record(
            event = "render_decision",
            fields =
                mapOf(
                    "kind" to decision.kind,
                    "reason" to decision.reason,
                    "confidencePercent" to decision.confidencePercent,
                    "scrollLines" to decision.scrollUpdate.lines.size,
                ),
        )
    }

    fun snapshot(): Map<String, Int> =
        mapOf(
            "noop" to noopCount.get(),
            "active_only" to activeOnlyCount.get(),
            "append_only" to appendOnlyCount.get(),
            "full_rewrite" to fullRewriteCount.get(),
        )

    fun reset() {
        noopCount.set(0)
        activeOnlyCount.set(0)
        appendOnlyCount.set(0)
        fullRewriteCount.set(0)
    }
}
