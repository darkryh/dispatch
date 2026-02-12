package com.ead.dispatch.sample.domain.agents.story_agent

import kotlinx.serialization.Serializable

@Serializable
data class StoryRequest(
    val text: String,
    val storyId: String,
    val fromDecisionPrompt: Boolean = false,
)
