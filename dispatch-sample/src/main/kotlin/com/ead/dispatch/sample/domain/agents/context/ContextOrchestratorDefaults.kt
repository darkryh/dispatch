package com.ead.dispatch.sample.domain.agents.context

import com.ead.dispatch.sample.domain.AIProvider
import com.ead.koog.context.orchestrator.api.ContextManagementConfig
import com.ead.koog.context.orchestrator.async.LlmAgentContextCompactorBackend

fun defaultContextOrchestratorConfig(maxContextTokens: Int): ContextManagementConfig =
    ContextManagementConfig(
        maxContextTokens = maxContextTokens,
        compactorBackend = LlmAgentContextCompactorBackend(
            promptExecutor = AIProvider.Sync.executor,
            llmModel = AIProvider.SubAgent.agent,
            temperature = 0.1,
            maxInputMessages = 120,
            maxCharsPerMessage = 700,
            maxOutputChars = 8_000,
        ),
    )
