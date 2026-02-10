package com.ead.koog.context.orchestrator.api

import com.ead.koog.context.orchestrator.state.ContextSnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Generic wrapper for agent outputs that also exposes live context metadata.
 */
data class ContextualResponse<T>(
    val value: T,
    val metadata: ContextualMetadata,
)

data class ContextualMetadata(
    val contextSnapshots: StateFlow<ContextSnapshot?>,
) {
    val latestSnapshot: ContextSnapshot?
        get() = contextSnapshots.value
}
