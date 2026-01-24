package com.ead.dispatch.sample.domain.agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.context.RollbackStrategy
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.extensions.runWithStartCheckpoint
import com.ead.dispatch.sample.domain.agents.chat_agent.node.nodeSetupAndStreamChatMode
import com.ead.dispatch.sample.domain.agents.chat_agent.tools.ChatCrudTools
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.flow.Flow

class ChatAgent(
    private val repository: StructuredIndexRepository,
    chatCrudTools: ChatCrudTools,
) {

    /**
     * Tool registry used by the chat agent. Tools are injected via Koog's DSL.
     */
    private val chatTools = chatCrudTools.asTools()

    private val toolRegistry = ToolRegistry {
        tools(chatTools)
    }

    /**
     * Builds a single-run agent for a chat request and streams the model output.
     * The agent is configured with persistence, but checkpoints are saved manually
     * for the streaming flow.
     */
    suspend fun run(session: Session, input: ChatRequest): Flow<StreamFrame> {
        val agent = AIAgent<ChatRequest, Flow<StreamFrame>>(
            systemPrompt = "You are chatting Agent Assistant",
            promptExecutor = AIProvider.deepseekPromptExecutor,
            llmModel = AIProvider.deepseekChatLlmModel,
            toolRegistry = toolRegistry,
            strategy = strategy<ChatRequest, Flow<StreamFrame>>("chat-mode.planner") {

                val chatAgentModel by nodeSetupAndStreamChatMode(repository = repository)

                edge(nodeStart forwardTo chatAgentModel transformed { it })
                edge(chatAgentModel forwardTo nodeFinish)
            },
            maxIterations = 50,
            temperature = 1.0,
            installFeatures = {
              install(Persistence) {
                  this.storage = Storage.provider
                  this.enableAutomaticPersistence = false
                  this.rollbackStrategy = RollbackStrategy.Default
              }
            },
            id = AIProvider.getChatAgentId(session.id),
        )

        return agent.runWithStartCheckpoint(AIProvider.getChatAgentId(session.id), input)
    }


}
