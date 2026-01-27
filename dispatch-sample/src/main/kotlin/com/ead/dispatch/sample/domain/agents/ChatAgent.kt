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
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.MemoryStore
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.extensions.runWithStartCheckpoint
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeLoadUserPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSaveUserPreferences
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSetupAndStreamChatMode
import com.ead.dispatch.sample.domain.agents.tools.CharacterTools
import com.ead.dispatch.sample.domain.agents.tools.LocationTools
import com.ead.dispatch.sample.domain.agents.tools.PlotTools
import com.ead.dispatch.sample.domain.agents.tools.StoryInfoTools
import com.ead.dispatch.sample.domain.agents.tools.WorldTools
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.flow.Flow

class ChatAgent(
    private val repository: StructuredIndexRepository,
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
    }

    /**
     * Builds a single-run agent for a chat request and streams the model output.
     * The agent is configured with persistence, but checkpoints are saved manually
     * for the streaming flow.
     */
    suspend fun run(session: Session, input: ChatRequest): Flow<StreamFrame> {
        val agentName = AIProvider.getChatAgentId(session.id)

        val agent = AIAgent<ChatRequest, Flow<StreamFrame>>(
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = toolRegistry,
            strategy = strategy<ChatRequest, Flow<StreamFrame>>("chat-mode.planner") {

                val loadUserPreferences by nodeLoadUserPreferences()
                val chatAgentModel by nodeSetupAndStreamChatMode(repository = repository)
                val saveUserPreferences by nodeSaveUserPreferences()

                edge(nodeStart forwardTo loadUserPreferences transformed { it })
                edge(loadUserPreferences forwardTo chatAgentModel transformed { it })
                edge(chatAgentModel forwardTo saveUserPreferences transformed { it })
                edge(saveUserPreferences forwardTo nodeFinish)
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
