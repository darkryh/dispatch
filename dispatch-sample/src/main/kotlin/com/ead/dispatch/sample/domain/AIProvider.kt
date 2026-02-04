package com.ead.dispatch.sample.domain

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.ead.dispatch.sample.domain.util.simpleDeepseekExecutor

object AIProvider {
    private val deepseekApiKey get() = System.getenv("DEEPSEEK_API_KEY")
        ?: throw Exception("DEEPSEEK_API_KEY not set in System Operative environment")

    val deepseekPromptExecutor get() = simpleDeepseekExecutor(deepseekApiKey)

    val deepseekChatLlmModel = DeepSeekModels.DeepSeekChat
    val deepseekReasonerLlmModel = DeepSeekModels.DeepSeekReasoner

    @Suppress("unused")
    val localPromptExecutor get() = simpleOllamaAIExecutor(baseUrl = "http://127.0.0.1:11434")

    @Suppress("unused")
    val localLlmModel get() = LLModel(
        provider = LLMProvider.Ollama,
        id = "deepseek-r1:7b",
        capabilities =listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.Schema.JSON.Basic,
            LLMCapability.Schema.JSON.Standard,
            LLMCapability.MultipleChoices,
        ),
        contextLength = 64_000,
        maxOutputTokens = 64_000
    )

    fun getChatAgentId(id : String) = "${id}:chat-agent"
    fun getStoryAgentId(id : String) = "${id}:story-agent"
    fun getCharacterAgentId(id: String) = "${id}:character-agent"
}
