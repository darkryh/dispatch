package com.ead.dispatch.sample.navigation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("chat")
data class ChatRoute(
    val conversationId: String? = null,
)

@Serializable
@SerialName("help")
data class HelpRoute(
    val from: String? = null,
)

@Serializable
@SerialName("session")
data class SessionRoute(
    val autoSelect: Boolean = false,
)

@Serializable
@SerialName("character")
data class CharacterRoute(
    val storyId: String? = null,
)

@Serializable
@SerialName("story-info")
data class StoryInfoRoute(
    val storyId: String? = null,
)
