package com.ead.dispatch.sample.domain.agents.chat_agent

import kotlinx.serialization.Serializable

@Serializable
data class ChatRequest(
    val text: String,
    val storyId: String,
)
