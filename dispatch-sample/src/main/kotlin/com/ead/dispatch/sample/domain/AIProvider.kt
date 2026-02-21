package com.ead.dispatch.sample.domain

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOllamaAIExecutor
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import ai.koog.prompt.executor.model.PromptExecutor
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

    private val openAiApiKey get() = System.getenv("OPENAI_API_KEY")
    ?: throw Exception("OPENAI_API_KEY not set in System Operative environment")

    val openAiPromptExecutor get() = simpleOpenAIExecutor(openAiApiKey)

    val chatGptNano = OpenAIModels.Chat.GPT5Nano
    val chatGptMini = OpenAIModels.Chat.GPT5Mini

    @Suppress("unused")
    val localPromptExecutor get() = simpleOllamaAIExecutor(baseUrl = "http://127.0.0.1:11434")

    @Suppress("unused")
    val localLlmModel get() = LLModel(
        provider = LLMProvider.Ollama,
        id = "qwen3:8b",
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

    /**
     * Role-based model configuration for the Chat Agent.
     */
    object Chat {
        var main: LLModel = deepseekChatLlmModel
        var intent: LLModel = deepseekChatLlmModel
        var fixer: LLModel = deepseekChatLlmModel
    }

    /**
     * Role-based model configuration for the Story Agent.
     */
    object Story {
        var main: LLModel = deepseekChatLlmModel
        var intent: LLModel = deepseekChatLlmModel
        var fixer: LLModel = deepseekChatLlmModel
    }

    /**
     * Centralized synchronization for executors.
     */
    object Sync {
        var chatExecutor: PromptExecutor = deepseekPromptExecutor
        var storyExecutor: PromptExecutor = deepseekPromptExecutor
        var subAgentExecutor: PromptExecutor = deepseekPromptExecutor
    }

    object SubAgent {
        var agent : LLModel = deepseekChatLlmModel
        var fixer: LLModel = deepseekChatLlmModel
    }

    fun getChatAgentId(id : String) = "${id}:chat-agent"
    fun getStoryAgentId(id : String) = "${id}:story-agent"
    fun getCharacterAgentId(id: String) = "${id}:character-agent"
    fun getLocationAgentId(id: String) = "${id}:location-agent"
    fun getWorldRuleAgentId(id: String) = "${id}:world-rule-agent"
    fun getCultureAgentId(id: String) = "${id}:culture-agent"
    fun getEventAgentId(id: String) = "${id}:event-agent"
    fun getOrganizationAgentId(id: String) = "${id}:organization-agent"
    fun getRelationshipAgentId(id: String) = "${id}:relationship-agent"
    fun getLocationFeatureAgentId(id: String) = "${id}:location-feature-agent"
    fun getArtifactAgentId(id: String) = "${id}:artifact-agent"
    fun getTimelineAgentId(id: String) = "${id}:timeline-agent"
}
