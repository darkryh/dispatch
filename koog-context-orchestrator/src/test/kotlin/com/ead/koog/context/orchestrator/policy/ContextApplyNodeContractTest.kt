package com.ead.koog.context.orchestrator.policy

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.testing.tools.getMockExecutor
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.message.Message
import com.ead.koog.context.orchestrator.api.ContextManagementConfig
import com.ead.koog.context.orchestrator.api.nodeApplyCompactedContext
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeLlm
import com.ead.koog.context.orchestrator.async.ContextCompactionArtifact
import com.ead.koog.context.orchestrator.async.ContextCompactionJob
import com.ead.koog.context.orchestrator.async.ContextCompactionResult
import com.ead.koog.context.orchestrator.async.ContextCompactionStore
import com.ead.koog.context.orchestrator.async.NoOpContextCompactorBackend
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ContextApplyNodeContractTest {

    @Test
    fun `before llm node does not apply artifact when apply node is absent`() = runBlocking {
        val store = StaticArtifactStore()

        val agent = AIAgent<String, Int>(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = DeepSeekModels.DeepSeekChat,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, Int>("context-no-apply") {
                val before by nodeManageContextBeforeLlm<String>(
                    configFactory = { maxTokens ->
                        ContextManagementConfig(
                            maxContextTokens = maxTokens,
                            compactionStore = store,
                            compactorBackend = NoOpContextCompactorBackend,
                        )
                    },
                )
                val inspect by node<String, Int>("inspect") {
                    llm.readSession {
                        prompt.messages
                            .filterIsInstance<Message.System>()
                            .count { msg -> msg.content.contains("[COMPACTED MEMORY ARTIFACT]") }
                    }
                }

                edge(nodeStart forwardTo before)
                edge(before forwardTo inspect)
                edge(inspect forwardTo nodeFinish)
            },
            id = "context-no-apply",
        )

        val result = agent.run("hello")
        assertEquals(0, result)
    }

    @Test
    fun `apply node applies artifact once before llm node`() = runBlocking {
        val store = StaticArtifactStore()

        val agent = AIAgent<String, Int>(
            promptExecutor = getMockExecutor(toolRegistry = ToolRegistry {}) {
                mockLLMAnswer("ok").asDefaultResponse
            },
            llmModel = DeepSeekModels.DeepSeekChat,
            toolRegistry = ToolRegistry {},
            strategy = strategy<String, Int>("context-with-apply") {
                val apply by nodeApplyCompactedContext<String>(
                    configFactory = { maxTokens ->
                        ContextManagementConfig(
                            maxContextTokens = maxTokens,
                            compactionStore = store,
                            compactorBackend = NoOpContextCompactorBackend,
                        )
                    },
                )
                val before by nodeManageContextBeforeLlm<String>(
                    configFactory = { maxTokens ->
                        ContextManagementConfig(
                            maxContextTokens = maxTokens,
                            compactionStore = store,
                            compactorBackend = NoOpContextCompactorBackend,
                        )
                    },
                )
                val inspect by node<String, Int>("inspect") {
                    llm.readSession {
                        prompt.messages
                            .filterIsInstance<Message.System>()
                            .count { msg -> msg.content.contains("[COMPACTED MEMORY ARTIFACT]") }
                    }
                }

                edge(nodeStart forwardTo apply)
                edge(apply forwardTo before)
                edge(before forwardTo inspect)
                edge(inspect forwardTo nodeFinish)
            },
            id = "context-with-apply",
        )

        val result = agent.run("hello")
        assertEquals(1, result)
    }

    private class StaticArtifactStore : ContextCompactionStore {
        private val artifact = ContextCompactionArtifact(
            agentId = "context-with-apply",
            sourceVersion = 1,
            sourceFingerprint = "fp-1",
            resultVersion = 1,
            mode = CompressionMode.AGGRESSIVE,
            text = "compacted",
        )

        override suspend fun enqueue(job: ContextCompactionJob): Boolean = false

        override suspend fun claimNext(workerId: String): ContextCompactionJob? = null

        override suspend fun complete(jobId: String, result: ContextCompactionResult) = Unit

        override suspend fun latestArtifact(agentId: String): ContextCompactionArtifact? = artifact.takeIf { it.agentId == agentId }

        override suspend fun latestVersion(agentId: String): Long? = artifact.resultVersion

        override suspend fun latestFingerprint(agentId: String): String? = artifact.sourceFingerprint
    }
}
