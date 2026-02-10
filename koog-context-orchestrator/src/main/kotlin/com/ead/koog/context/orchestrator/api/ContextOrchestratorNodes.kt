package com.ead.koog.context.orchestrator.api

import ai.koog.agents.core.agent.context.AIAgentGraphContextBase
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase

/**
 * Strategy-graph integration for [KoogContextOrchestrator].
 *
 * This lets projects use context management as standard graph nodes,
 * similar to other Koog node helpers.
 */
enum class ContextNodeStage {
    BEFORE_LLM,
    AFTER_LLM,
    BEFORE_TOOL_LOOP,
    AFTER_TOOL_LOOP,
}

@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeManageContext(
    orchestrator: KoogContextOrchestrator? = null,
    stage: ContextNodeStage,
    name: String? = null,
    noinline configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
    crossinline hints: AIAgentGraphContextBase.(T) -> ContextHints = { ContextHints() },
): AIAgentNodeDelegate<T, T> =
    node(name ?: defaultNodeName(stage)) { input ->
        val runtimeOrchestrator = resolveContextOrchestrator(
            explicit = orchestrator,
            configFactory = configFactory,
        )
        val resolvedHints = hints(input)

        when (stage) {
            ContextNodeStage.BEFORE_LLM -> runtimeOrchestrator.beforeLlmCall(this, resolvedHints)
            ContextNodeStage.AFTER_LLM -> runtimeOrchestrator.afterLlmCall(this)
            ContextNodeStage.BEFORE_TOOL_LOOP -> runtimeOrchestrator.beforeToolLoop(this, resolvedHints)
            ContextNodeStage.AFTER_TOOL_LOOP -> runtimeOrchestrator.afterToolLoop(this)
        }

        input
    }

@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeManageContextBeforeLlm(
    orchestrator: KoogContextOrchestrator? = null,
    name: String? = null,
    noinline configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
    crossinline hints: AIAgentGraphContextBase.(T) -> ContextHints = { ContextHints() },
): AIAgentNodeDelegate<T, T> =
    nodeManageContext(
        orchestrator = orchestrator,
        stage = ContextNodeStage.BEFORE_LLM,
        name = name,
        configFactory = configFactory,
        hints = hints,
    )

@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeManageContextAfterLlm(
    orchestrator: KoogContextOrchestrator? = null,
    name: String? = null,
    noinline configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
): AIAgentNodeDelegate<T, T> =
    nodeManageContext(
        orchestrator = orchestrator,
        stage = ContextNodeStage.AFTER_LLM,
        name = name,
        configFactory = configFactory,
    )

@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeManageContextBeforeToolLoop(
    orchestrator: KoogContextOrchestrator? = null,
    name: String? = null,
    noinline configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
    crossinline hints: AIAgentGraphContextBase.(T) -> ContextHints = { ContextHints() },
): AIAgentNodeDelegate<T, T> =
    nodeManageContext(
        orchestrator = orchestrator,
        stage = ContextNodeStage.BEFORE_TOOL_LOOP,
        name = name,
        configFactory = configFactory,
        hints = hints,
    )

@AIAgentBuilderDslMarker
inline fun <reified T> AIAgentSubgraphBuilderBase<*, *>.nodeManageContextAfterToolLoop(
    orchestrator: KoogContextOrchestrator? = null,
    name: String? = null,
    noinline configFactory: (maxContextTokens: Int) -> ContextManagementConfig = { maxTokens ->
        ContextManagementConfig(maxContextTokens = maxTokens)
    },
): AIAgentNodeDelegate<T, T> =
    nodeManageContext(
        orchestrator = orchestrator,
        stage = ContextNodeStage.AFTER_TOOL_LOOP,
        name = name,
        configFactory = configFactory,
    )

@PublishedApi
internal fun defaultNodeName(stage: ContextNodeStage): String = when (stage) {
    ContextNodeStage.BEFORE_LLM -> "context-before-llm"
    ContextNodeStage.AFTER_LLM -> "context-after-llm"
    ContextNodeStage.BEFORE_TOOL_LOOP -> "context-before-tool-loop"
    ContextNodeStage.AFTER_TOOL_LOOP -> "context-after-tool-loop"
}
