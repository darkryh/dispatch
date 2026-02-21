package com.ead.dispatch.sample.domain.agents.chat_agent.classifier.node

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.dsl.builder.AIAgentBuilderDslMarker
import ai.koog.agents.core.dsl.builder.AIAgentNodeDelegate
import ai.koog.agents.core.dsl.builder.AIAgentSubgraphBuilderBase
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.classifier.ChatClassifierResponse
import com.ead.dispatch.sample.domain.agents.chat_agent.classifier.chatClassifierPrompt


@Suppress("unused") // reasoner deepseek model has crashes using koog for the moment unused to decide which model to use
@AIAgentBuilderDslMarker
fun AIAgentSubgraphBuilderBase<*, *>.nodeClassifyingLlmModelRequest(
    name: String? = null
): AIAgentNodeDelegate<ChatRequest, Pair<ChatRequest, Result<StructuredResponse<ChatClassifierResponse>>>> =
    node(name) { input ->
        classifierAgentRun(
            model = AIProvider.Chat.intent,
            chatRequest = input
        )
    }

private suspend fun AIAgentContext.classifierAgentRun(
    model: LLModel,
    chatRequest: ChatRequest
): Pair<ChatRequest, Result<StructuredResponse<ChatClassifierResponse>>> = llm.writeSession {
    this.model = model

    rewritePrompt {
        chatClassifierPrompt(
            flashModel = AIProvider.Chat.intent.id,
            proModel = AIProvider.Chat.intent.id,
            promptBuilder = {
                user(chatRequest.text)
            }
        )
    }

    Pair(
        first = chatRequest,
        second = requestLLMStructured<ChatClassifierResponse>(
            fixingParser = StructureFixingParser(
                model = AIProvider.Chat.fixer,
                retries = 2
            )
        )
    )
}