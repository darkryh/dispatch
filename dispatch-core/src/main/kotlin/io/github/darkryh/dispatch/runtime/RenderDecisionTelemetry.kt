package io.github.darkryh.dispatch.runtime

import io.github.darkryh.dispatch.render.RenderDiagnostics

/**
 * Emits per-decision render diagnostics to the sidecar JSON Lines log when enabled.
 *
 * Enable output with the `DISPATCH_DIAGNOSTICS_FILE` environment variable. When disabled (the
 * production default) this is a no-op: the `fields` map is not even constructed.
 */
internal object RenderDecisionTelemetry {
    fun record(decision: RenderDecision) {
        if (!RenderDiagnostics.isEnabled) return
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
}
