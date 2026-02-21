package com.ead.dispatch.sample.domain.agents.chat_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.tokenizer.feature.MessageTokenizer
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.tokenizer.SimpleRegexBasedTokenizer
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.MemoryStore
import com.ead.dispatch.sample.domain.Pathing
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.ChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.extensions.runWithStartCheckpoint
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeAuditTurn
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeLoadChatPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSaveChatPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.subgraphClassifyIntent
import com.ead.dispatch.sample.domain.agents.chat_agent.node.subgraphSetupAndStreamChatMode
import com.ead.dispatch.sample.domain.agents.tools.CharacterTools
import com.ead.dispatch.sample.domain.agents.tools.InteractionTools
import com.ead.dispatch.sample.domain.agents.tools.LocationTools
import com.ead.dispatch.sample.domain.agents.tools.PlotTools
import com.ead.dispatch.sample.domain.agents.tools.StoryInfoTools
import com.ead.dispatch.sample.domain.agents.tools.WorldTools
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.koog.benchmark.core.JsonlBenchmarkRecorder
import com.ead.koog.benchmark.koog.KoogBenchmark
import com.ead.koog.context.orchestrator.api.ContextualResponse
import kotlinx.coroutines.flow.Flow

class KoogChatAgent(
    private val repository: StructuredIndexRepository,
    private val ragContextService: RagContextService,
) : ChatAgent {
    private val benchmarkEnabled: Boolean = true
    private val benchmarkRecorder by lazy {
        JsonlBenchmarkRecorder(
            outputDirectory = Pathing.applicationDirectory.resolve("benchmarks"),
            fileNamePrefix = "chat-agent-runs",
        )
    }

    /**
     * Tool registry used by the chat agent. Tools are injected via Koog's DSL.
     */
    private val toolRegistry = ToolRegistry.Companion {
        tools(StoryInfoTools(repository).asTools())
        tools(LocationTools(repository).asTools())
        tools(CharacterTools(repository).asTools())
        tools(WorldTools(repository).asTools())
        tools(PlotTools(repository).asTools())
        tools(InteractionTools(repository).asTools())
    }

    /**
     * Builds a single-run agent for a chat request and streams the model output.
     * The agent is configured with persistence, but checkpoints are saved manually
     * for the streaming flow.
     */
    override suspend fun run(session: Session, input: ChatRequest): ContextualResponse<Flow<StreamFrame>> {
        val agentName = AIProvider.getChatAgentId(session.id)

        val agent = AIAgent<ChatRequest, ContextualResponse<Flow<StreamFrame>>>(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.Chat.main,
            strategy = strategy<ChatRequest, ContextualResponse<Flow<StreamFrame>>>("chat-mode.planner") {
                val chatIntent by subgraphClassifyIntent()

                val loadChatPreferences by nodeLoadChatPreferences()

                val chatStreamingResponse by subgraphSetupAndStreamChatMode(repository, ragContextService)

                val saveChatPreferences by nodeSaveChatPreferences()

                val auditTurn by nodeAuditTurn()

                edge(nodeStart forwardTo chatIntent)

                edge(chatIntent forwardTo loadChatPreferences)

                edge(loadChatPreferences forwardTo chatStreamingResponse)

                edge(chatStreamingResponse forwardTo saveChatPreferences)

                edge(saveChatPreferences forwardTo auditTurn)

                edge(auditTurn forwardTo nodeFinish)
            },
            responseProcessor = null,
            toolRegistry = toolRegistry,
            maxIterations = 50,
            temperature = 1.0,
            installFeatures = {
                install(KoogBenchmark.Feature) {
                    enabled = benchmarkEnabled
                    recorder = benchmarkRecorder
                    staticAttributes = mapOf(
                        "dispatch.agent_kind" to "chat",
                        "dispatch.module" to "dispatch-sample",
                    )
                    extraAttributesProvider = { context ->
                        mapOf(
                            "dispatch.strategy_name" to context.strategyName,
                        )
                    }
                }
                install(MessageTokenizer.Feature) {
                    tokenizer = SimpleRegexBasedTokenizer()
                    enableCaching = true
                }
                install(EventHandler.Feature) {
                    onAgentExecutionFailed { eventContext ->
                        System.err.println(
                            "chat-agent execution failed: runId=${eventContext.runId}, error=${eventContext.throwable.message}"
                        )
                    }
                    onToolValidationFailed { eventContext ->
                        System.err.println(
                            "chat-agent tool validation failed: tool=${eventContext.toolName}, error=${eventContext.error.message}"
                        )
                    }
                }
                install(Persistence.Feature) {
                    this.storage = Storage.provider
                    this.enableAutomaticPersistence = false
                    this.rollbackStrategy = RollbackStrategy.Default
                }
                install(AgentMemory.Feature) {
                    memoryProvider = MemoryStore.provider
                    productName = "dispatch"
                    organizationName = "ead"
                    featureName = "chat"
                    this.agentName = agentName
                }
            },
            id = agentName,
        )

        return agent.runWithStartCheckpoint(
            agentId = agentName,
            input = input,
            startNodePath = "$agentName/chat-mode.planner/chat-intent-flow",
        )
    }


}
