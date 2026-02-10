package com.ead.dispatch.sample.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.memory.feature.AgentMemory
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.streaming.StreamFrame
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.koog.context.orchestrator.api.TaskPhase
import com.ead.koog.context.orchestrator.api.nodeManageContextAfterLlm
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeLlm
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.MemoryStore
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.PreferencesMemory
import com.ead.dispatch.sample.domain.agents.chat_agent.extensions.runWithStartCheckpoint
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeApplyTurnPolicy
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeAuditTurn
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeClassifyIntent
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeLoadUserPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSaveUserPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSetupAndStreamChatMode
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnInput
import com.ead.dispatch.sample.domain.agents.tools.CharacterTools
import com.ead.dispatch.sample.domain.agents.tools.LocationTools
import com.ead.dispatch.sample.domain.agents.tools.PlotTools
import com.ead.dispatch.sample.domain.agents.tools.StoryInfoTools
import com.ead.dispatch.sample.domain.agents.tools.InteractionTools
import com.ead.dispatch.sample.domain.agents.tools.WorldTools
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.flow.Flow

class ChatAgent(
    private val repository: StructuredIndexRepository,
    private val ragContextService: RagContextService,
) {

    /**
     * Tool registry used by the chat agent. Tools are injected via Koog's DSL.
     */
    private val toolRegistry = ToolRegistry {
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
    suspend fun run(session: Session, input: ChatRequest): ContextualResponse<Flow<StreamFrame>> {
        val agentName = AIProvider.getChatAgentId(session.id)

        val agent = AIAgent<ChatRequest, ContextualResponse<Flow<StreamFrame>>>(
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = toolRegistry,
            strategy = strategy<ChatRequest, ContextualResponse<Flow<StreamFrame>>>("chat-mode.planner") {
                val classifyIntent by nodeClassifyIntent()

                val applyTurnPolicy by nodeApplyTurnPolicy()

                val loadUserPreferences by nodeLoadUserPreferences()

                val contextBeforeLlm by nodeManageContextBeforeLlm<ChatTurnInput>(
                    hints = { turnInput ->
                        ContextHints(
                            phase = TaskPhase.EXECUTION,
                            factConcepts = PreferencesMemory.userConcepts,
                            continuityPacket = ContinuityPacket(
                                objective = "Follow chat turn policy: ${turnInput.policy.decisionPath.name}.",
                                constraints = listOf(
                                    "write_tools_allowed=${turnInput.policy.allowWriteTools}",
                                    "require_selector_for_destructive=${turnInput.policy.requireSelectorForDestructive}",
                                ),
                                criticalReferences = listOf("storyId=${turnInput.request.storyId}"),
                            )
                        )
                    }
                )

                val chatAgentModel by nodeSetupAndStreamChatMode(
                    repository = repository,
                    ragContextService = ragContextService
                )

                val contextAfterLlm by nodeManageContextAfterLlm<ContextualResponse<Flow<StreamFrame>>>()

                val saveUserPreferences by nodeSaveUserPreferences()

                val auditTurn by nodeAuditTurn()

                edge(nodeStart forwardTo classifyIntent)

                edge(classifyIntent forwardTo applyTurnPolicy)
                edge(applyTurnPolicy forwardTo loadUserPreferences)
                edge(loadUserPreferences forwardTo contextBeforeLlm)

                edge(contextBeforeLlm forwardTo chatAgentModel)

                edge(chatAgentModel forwardTo contextAfterLlm)

                edge(contextAfterLlm forwardTo saveUserPreferences)

                edge(saveUserPreferences forwardTo auditTurn)
                edge(auditTurn forwardTo nodeFinish)
            },
            maxIterations = 50,
            temperature = 1.0,
            installFeatures = {
              install(Persistence) {
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

        return agent.runWithStartCheckpoint(agentName, input)
    }


}
