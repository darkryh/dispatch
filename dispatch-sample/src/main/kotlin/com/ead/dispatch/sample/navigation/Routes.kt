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
@SerialName("characters-list")
data class CharacterListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("locations-list")
data class LocationListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("arcs-list")
data class ArcListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("world-rules-list")
data class WorldRuleListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("cultures-list")
data class CultureListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("events-list")
data class EventListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("organizations-list")
data class OrganizationListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("relationships-list")
data class RelationshipListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("location-features-list")
data class LocationFeatureListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("artifacts-list")
data class ArtifactListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("timeline-list")
data class TimelineListRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("story-chat")
data class StoryChatRoute(
    val storyId: String? = null,
) : NavKey

@Serializable
@SerialName("entity-editor")
data class EntityEditorRoute(
    val type: String = EntityOptionType.LOCATIONS.id,
    val storyId: String? = null,
    val entityId: String? = null,
) : NavKey
