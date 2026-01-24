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
    val mode: String? = null,
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
    val characterId: String? = null,
)

@Serializable
@SerialName("story-info")
data class StoryInfoRoute(
    val storyId: String? = null,
)

@Serializable
@SerialName("story-summary")
data class StorySummaryRoute(
    val storyId: String? = null,
)

@Serializable
@SerialName("entity-list")
data class EntityListRoute(
    val type: String = "characters",
    val storyId: String? = null,
)
