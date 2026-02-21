package com.ead.dispatch.sample.domain.agents.internal.timeline_agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.internal.timeline_agent.node.nodeGenerateTimelineDraft

class TimelineAgent {
    suspend fun generateDraft(request: TimelineAIRequest): TimelineAIDraft {
        val agentName = AIProvider.getTimelineAgentId(request.storyId)
        val temperature = when (request.mode) {
            TimelineAIMode.NORMAL -> 0.7
            TimelineAIMode.CREATIVE -> 1.0
        }
        val agent = AIAgent<TimelineAIRequest, Result<StructuredResponse<TimelineAIDraft>>>(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.SubAgent.agent,
            toolRegistry = ToolRegistry {},
            strategy = strategy<TimelineAIRequest, Result<StructuredResponse<TimelineAIDraft>>>("timeline-draft") {
                val draftNode by nodeGenerateTimelineDraft()
                edge(nodeStart forwardTo draftNode)
                edge(draftNode forwardTo nodeFinish transformed { it })
            },
            maxIterations = 10,
            temperature = temperature,
            id = agentName,
        )

        val response = agent.run(request)
        val structured = response.getOrThrow()
        return structured.data
    }
}
