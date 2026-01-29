package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient

class ChatAgentEmbedder {

    private val client = OllamaClient(baseUrl = "http://127.0.0.1:11434")

    val chatModeLlmEmbedder = LLMEmbedder(client, OllamaEmbeddingModels.ALL_MINI_LM)
    val storyModeLlmEmbedder = LLMEmbedder(client, OllamaEmbeddingModels.NOMIC_EMBED_TEXT)
}