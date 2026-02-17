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
import com.ead.dispatch.sample.domain.agents.chat_agent.extensions.runWithStartCheckpoint
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.story_agent.node.StoryTurnInput
import com.ead.dispatch.sample.domain.agents.story_agent.node.nodeApplyStoryTurnPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.node.nodeAuditStoryTurn
import com.ead.dispatch.sample.domain.agents.story_agent.node.nodeClassifyStoryIntent
import com.ead.dispatch.sample.domain.agents.story_agent.node.nodeSetupAndStreamStoryMode
import com.ead.dispatch.sample.domain.agents.tools.InteractionTools
import com.ead.dispatch.sample.domain.agents.tools.StoryDraftTools
import com.ead.dispatch.sample.domain.agents.tools.StoryInfoTools
import com.ead.dispatch.sample.domain.agents.tools.StoryStructureTools
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.koog.context.orchestrator.api.ContextHints
import com.ead.koog.context.orchestrator.api.ContextualResponse
import com.ead.koog.context.orchestrator.api.TaskPhase
import com.ead.koog.context.orchestrator.api.nodeManageContextAfterLlm
import com.ead.koog.context.orchestrator.api.nodeManageContextBeforeLlm
import com.ead.koog.context.orchestrator.state.ContinuityPacket
import kotlinx.coroutines.flow.Flow

class StoryAgent(
    private val repository: StructuredIndexRepository,
    private val ragContextService: RagContextService,
) {

    private val toolRegistry = ToolRegistry {
        tools(StoryInfoTools(repository).asTools())
        tools(StoryStructureTools(repository).asTools())
        tools(StoryDraftTools(repository).asTools())
        tools(InteractionTools(repository).asTools())
    }

    suspend fun run(session: Session, input: StoryRequest): ContextualResponse<Flow<StreamFrame>> {
        val agentName = AIProvider.getStoryAgentId(session.id)

        val agent = AIAgent<StoryRequest, ContextualResponse<Flow<StreamFrame>>>(
            promptExecutor = AIProvider.Sync.storyExecutor,
            llmModel = AIProvider.Story.main,
            toolRegistry = toolRegistry,
            strategy = strategy<StoryRequest, ContextualResponse<Flow<StreamFrame>>>("story-mode.writer") {
                val classifyIntent by nodeClassifyStoryIntent()

                val applyTurnPolicy by nodeApplyStoryTurnPolicy()

                val contextBeforeLlm by nodeManageContextBeforeLlm<StoryTurnInput>(
                    hints = { turnInput ->
                        ContextHints(
                            phase = TaskPhase.EXECUTION,
                            continuityPacket = ContinuityPacket(
                                objective = "Execute story mode turn policy: ${turnInput.policy.decisionPath.name}.",
                                constraints = listOf(
                                    "write_tools_allowed=${turnInput.policy.allowWriteTools}",
                                    "require_selector_for_destructive=${turnInput.policy.requireSelectorForDestructive}",
                                ),
                                criticalReferences = listOf("storyId=${turnInput.request.storyId}"),
                            )
                        )
                    }
                )

                val storyAgentModel by nodeSetupAndStreamStoryMode(
                    repository = repository,
                    ragContextService = ragContextService,
                )

                val contextAfterLlm by nodeManageContextAfterLlm<ContextualResponse<Flow<StreamFrame>>>()

                val auditTurn by nodeAuditStoryTurn()

                edge(nodeStart forwardTo classifyIntent)

                edge(classifyIntent forwardTo applyTurnPolicy)
                edge(applyTurnPolicy forwardTo contextBeforeLlm)

                edge(contextBeforeLlm forwardTo storyAgentModel)

                edge(storyAgentModel forwardTo contextAfterLlm)
                edge(contextAfterLlm forwardTo auditTurn)
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
            },
            id = agentName,
        )

        return agent.runWithStartCheckpoint(
            agentId = agentName,
            input = input,
            startNodePath = "$agentName/story-mode.writer/story-classify-intent",
        )
    }
}
