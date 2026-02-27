package com.ead.dispatch.sample.domain.agents.story_agent.node

import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphDelegate
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.story_agent.memory.service.StoryContinuityMemoryService
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.koog.context.orchestrator.api.ContextualResponse
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
        val storyAgentModel by nodeSetupAndStreamStoryMode(
            repository = repository,
            ragContextService = ragContextService,
            continuityMemoryService = continuityMemoryService,
        )
        edge(nodeStart forwardTo storyAgentModel)
        edge(storyAgentModel forwardTo nodeFinish)
    }
