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
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.koog.context.orchestrator.api.ContextualResponse
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
        val chatAgentModel by nodeSetupAndStreamChatMode(
            repository = repository,
            ragContextService = ragContextService
        )
        edge(nodeStart forwardTo chatAgentModel)
        edge(chatAgentModel forwardTo nodeFinish)
    }
