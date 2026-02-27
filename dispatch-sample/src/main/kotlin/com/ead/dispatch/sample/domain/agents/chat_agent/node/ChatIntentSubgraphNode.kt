package com.ead.dispatch.sample.domain.agents.chat_agent.node

import ai.koog.agents.core.agent.entity.ToolSelectionStrategy
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.processor.ResponseProcessor
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.SelectorPreferencesMemory
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.context.defaultContextOrchestratorConfig
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.koog.context.orchestrator.api.TaskPhase
import com.ead.koog.context.orchestrator.api.nodeManageContextAfterLlm
import com.ead.koog.context.orchestrator.api.nodeApplyCompactedContext
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeLlm
import com.ead.koog.context.orchestrator.api.nodeManageContextEndTurn
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import kotlinx.coroutines.flow.Flow

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.subgraphClassifyIntent(
    name: String? = null,
    llmModel: LLModel? = AIProvider.Chat.intent,
    llmParams: LLMParams? = LLMParams(temperature = .2),
    responseProcessor: ResponseProcessor? = null,
): AIAgentSubgraphDelegate<ChatRequest, ChatTurnInput> =
    subgraph(
        name = name ?: "chat-intent-flow",
        tools = ToolRegistry.EMPTY.tools,
        llmModel = llmModel,
        llmParams = llmParams,
        responseProcessor = responseProcessor
    ) {
        val classifyIntent by nodeClassifyIntent()
        val applyTurnPolicy by nodeApplyTurnPolicy()

        edge(nodeStart forwardTo classifyIntent)
        edge(classifyIntent forwardTo applyTurnPolicy)
        edge(applyTurnPolicy forwardTo nodeFinish)
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.subgraphSetupAndStreamChatMode(
    repository: StructuredIndexRepository,
    ragContextService : RagContextService,
    name: String? = null,
    toolSelectionStrategy : ToolSelectionStrategy = ToolSelectionStrategy.NONE,
    llmModel: LLModel? = AIProvider.Chat.main,
    llmParams: LLMParams? = LLMParams(temperature = .2),
    responseProcessor: ResponseProcessor? = null,
): AIAgentSubgraphDelegate<ChatTurnInput, ContextualResponse<Flow<StreamFrame>>> =
    subgraph(
        name = name ?: "chat-streaming-flow",
        toolSelectionStrategy = toolSelectionStrategy,
        llmModel = llmModel,
        llmParams = llmParams,
        responseProcessor = responseProcessor
    ) {
        val applyCompactedContext by nodeApplyCompactedContext<ChatTurnInput>(
            configFactory = ::defaultContextOrchestratorConfig,
        )

        val contextBeforeLlm by nodeManageContextBeforeLlm<ChatTurnInput>(
            configFactory = ::defaultContextOrchestratorConfig,
            hints = { turnInput ->
                ContextHints(
                    phase = TaskPhase.EXECUTION,
                    factConcepts = SelectorPreferencesMemory.userConcepts,
                    continuityPacket = ContinuityPacket(
                        objective = "Follow chat turn policy: ${turnInput.policy.decisionPath.name}/${turnInput.policy.resolvedAction.name}.",
                        constraints = listOf(
                            "write_tools_allowed=${turnInput.policy.allowWriteTools}",
                            "require_selector_for_destructive=${turnInput.policy.requireSelectorForDestructive}",
                            "require_selector_for_creative=${turnInput.policy.requireSelectorForCreative}",
                            "confidence_band=${turnInput.policy.confidenceBand.name}",
                            "risk_class=${turnInput.policy.riskClass.name}",
                        ),
                        criticalReferences = listOf("storyId=${turnInput.request.storyId}"),
                    )
                )
            }
        )

        val chatAgentModel by nodeSetupAndStreamChatMode(
            repository = repository,
            ragContextService = ragContextService
        )

        val contextAfterLlm by nodeManageContextAfterLlm<ContextualResponse<Flow<StreamFrame>>>(
            configFactory = ::defaultContextOrchestratorConfig,
        )

        val contextEndTurn by nodeManageContextEndTurn<ContextualResponse<Flow<StreamFrame>>>(
            configFactory = ::defaultContextOrchestratorConfig,
            hints = { response ->
                val snapshot = response.metadata.latestSnapshot
                ContextHints(
                    phase = TaskPhase.FINALIZATION,
                    continuityPacket = snapshot?.continuityPacket,
                )
            }
        )

        edge(nodeStart forwardTo applyCompactedContext)
        edge(applyCompactedContext forwardTo contextBeforeLlm)
        edge(contextBeforeLlm forwardTo chatAgentModel)
        edge(chatAgentModel forwardTo contextAfterLlm)
        edge(contextAfterLlm forwardTo contextEndTurn)
        edge(contextEndTurn forwardTo nodeFinish)
    }
