package com.ead.dispatch.sample.navigation

import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("chat")
data class ChatRoute(
    val conversationId: String? = null,
) : NavKey

@Serializable
@SerialName("session")
data class SessionRoute(
    val autoSelect: Boolean = false,
) : NavKey

@Serializable
@SerialName("character")
data class CharacterRoute(
    val storyId: String? = null,
    val characterId: String? = null,
) : NavKey

@Serializable
@SerialName("story-chat")
data class StoryChatRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("entity-list")
data class EntityOptionRoute(
    val type: String = EntityOptionType.CHARACTERS.id,
    val storyId: String? = null,
) : NavKey
