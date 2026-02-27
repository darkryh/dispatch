package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.policy.CompressionMode

/**
 * Per-risk-zone compression policy.
 *
 * This allows callers to fully control compression behavior by zone.
 */
data class ZoneCompressionPolicy(
    val mode: CompressionMode,
    val timing: CompressionTiming,
    val minRecentToolCalls: Int = 0,
) {
    init {
        require(minRecentToolCalls >= 0) { "minRecentToolCalls must be >= 0." }
    }
}

