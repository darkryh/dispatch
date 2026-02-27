package com.ead.dispatch.sample.domain.agents

import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.koog.context.orchestrator.api.ContextRunOutput
import kotlinx.coroutines.flow.Flow

fun interface ChatAgent {
    suspend fun run(session: Session, input: ChatRequest): ContextRunOutput<Flow<StreamFrame>>
}
