package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.embeddings.local.OllamaEmbeddingModels
import ai.koog.prompt.executor.ollama.client.OllamaClient

class ChatAgentEmbedder {

    val client = OllamaClient()
    val embedder = LLMEmbedder(client, OllamaEmbeddingModels.ALL_MINI_LM)


}