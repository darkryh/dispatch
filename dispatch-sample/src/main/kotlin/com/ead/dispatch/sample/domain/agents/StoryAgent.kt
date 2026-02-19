package com.ead.dispatch.sample.domain.agents

import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.koog.context.orchestrator.api.ContextualResponse
import kotlinx.coroutines.flow.Flow

fun interface StoryAgent {
    suspend fun run(session: Session, input: StoryRequest): ContextualResponse<Flow<StreamFrame>>
}
