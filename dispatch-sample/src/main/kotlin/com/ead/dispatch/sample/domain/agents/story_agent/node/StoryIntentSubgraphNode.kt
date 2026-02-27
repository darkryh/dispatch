package com.ead.dispatch.sample.domain.agents.story_agent.node

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.context.defaultContextOrchestratorConfig
import com.ead.dispatch.sample.domain.agents.story_agent.memory.service.StoryContinuityMemoryService
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
fun AIAgentSubgraphBuilderBase<*, *>.subgraphClassifyStoryIntent(
    name: String? = null,
): AIAgentSubgraphDelegate<StoryRequest, StoryTurnInput> =
    subgraph(
        name = name ?: "story-intent-flow",
        llmParams = LLMParams(temperature = .2)
    ) {
        val classifyIntent by nodeClassifyStoryIntent()
        val applyTurnPolicy by nodeApplyStoryTurnPolicy()

        edge(nodeStart forwardTo classifyIntent)
        edge(classifyIntent forwardTo applyTurnPolicy)
        edge(applyTurnPolicy forwardTo nodeFinish)
    }

@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.subgraphSetupAndStreamStoryMode(
    repository: StructuredIndexRepository,
    ragContextService: RagContextService,
    continuityMemoryService: StoryContinuityMemoryService,
    name: String? = null,
): AIAgentSubgraphDelegate<StoryTurnInput, ContextualResponse<Flow<StreamFrame>>> =
    subgraph(
        name = name ?: "story-streaming-flow",
        llmParams = LLMParams(temperature = 1.0)
    ) {
        val applyCompactedContext by nodeApplyCompactedContext<StoryTurnInput>(
            configFactory = ::defaultContextOrchestratorConfig,
        )

        val contextBeforeLlm by nodeManageContextBeforeLlm<StoryTurnInput>(
            configFactory = ::defaultContextOrchestratorConfig,
            hints = { turnInput ->
                ContextHints(
                    phase = TaskPhase.EXECUTION,
                    factConcepts = PreferencesMemory.userConcepts,
                    continuityPacket = ContinuityPacket(
                        objective = "Execute story mode turn policy: ${turnInput.policy.decisionPath.name}/${turnInput.policy.resolvedAction.name}.",
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

        val storyAgentModel by nodeSetupAndStreamStoryMode(
            repository = repository,
            ragContextService = ragContextService,
            continuityMemoryService = continuityMemoryService,
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
        edge(contextBeforeLlm forwardTo storyAgentModel)
        edge(storyAgentModel forwardTo contextAfterLlm)
        edge(contextAfterLlm forwardTo contextEndTurn)
        edge(contextEndTurn forwardTo nodeFinish)
    }
