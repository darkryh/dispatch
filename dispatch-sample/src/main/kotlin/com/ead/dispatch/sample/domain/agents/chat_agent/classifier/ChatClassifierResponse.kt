package com.ead.dispatch.sample.domain.agents.chat_agent.classifier

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatClassifierResponse(
    @SerialName("reasoning")
    val reasoning: String,
    @SerialName("model_choice")
    val modelChoice: String
)