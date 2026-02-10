package com.ead.koog.context.orchestrator.api

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.agent.entity.AIAgentStorageKey

private val contextOrchestratorKey: AIAgentStorageKey<KoogContextOrchestrator> =
    AIAgentStorageKey("koog-context-orchestrator.runtime")

/**
 * Resolves a runtime orchestrator for the current agent context.
 *
 * - If [explicit] is provided, it is used as-is.
 * - Otherwise, a per-agent orchestrator is lazily created from the active LLM model context length,
 *   then cached in agent storage for reuse across nodes in the same run.
 */
suspend fun AIAgentGraphContextBase.resolveContextOrchestrator(
    explicit: KoogContextOrchestrator? = null,
    configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
): KoogContextOrchestrator {
    explicit?.let { return it }

    val existing = get(contextOrchestratorKey) as? KoogContextOrchestrator
    if (existing != null) return existing

    val maxTokens = llm.readSession {
        val contextLength = model.contextLength.toInt()
        if (contextLength <= 0) {
            throw IllegalStateException(
                "Unable to resolve max context tokens from model '${model.id}'. " +
                    "Model contextLength must be > 0."
            )
        }
        contextLength
    }

    val created = KoogContextOrchestrator(configFactory(maxTokens))
    store(contextOrchestratorKey, created)
    return created
}
