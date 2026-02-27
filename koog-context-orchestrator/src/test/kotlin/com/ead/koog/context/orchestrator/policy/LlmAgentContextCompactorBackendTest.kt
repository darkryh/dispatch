package com.ead.koog.context.orchestrator.policy

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.async.ContextCompactionJob
import com.ead.koog.context.orchestrator.async.ContextCompactionJobStatus
import com.ead.koog.context.orchestrator.async.LlmAgentContextCompactorBackend
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LlmAgentContextCompactorBackendTest {

    @Test
    fun `llm backend compacts job and returns artifact`() = runBlocking {
        val backend = LlmAgentContextCompactorBackend(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("compressed summary with key constraints").asDefaultResponse
            },
            llmModel = DeepSeekModels.DeepSeekChat,
        )

        val result = backend.compact(
            ContextCompactionJob(
                agentId = "agent-llm",
                sourceVersion = 42,
                sourceFingerprint = "fp-42",
                mode = CompressionMode.AGGRESSIVE,
                riskZone = ContextRiskZone.CRITICAL,
                hints = ContextHints(),
                promptMessages = listOf("u:hello", "a:world", "u:please summarize"),
            ),
        )

        assertEquals(ContextCompactionJobStatus.SUCCEEDED, result.status)
        val artifact = result.artifact
        assertNotNull(artifact)
        assertEquals("agent-llm", artifact.agentId)
        assertEquals(42, artifact.resultVersion)
        assertTrue(artifact.text.contains("compressed summary"))
    }
}
