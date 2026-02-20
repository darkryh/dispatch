package com.ead.dispatch.sample.domain.agents.chat_agent

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val text: String,
    val storyId: String,
    val fromDecisionPrompt: Boolean = false,
    val decisionContext: ChatDecisionContext? = null,
)

@Serializable
data class ChatDecisionContext(
    val promptId: String? = null,
    val question: String,
    val optionLabels: List<String> = emptyList(),
    val selectedValue: String,
    val isCustomSelection: Boolean = false,
)
