package com.ead.dispatch.sample.navigation

import com.ead.dispatch.navigation.NavKey
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
@SerialName("arc-editor")
data class ArcEditorRoute(
    val storyId: String? = null,
    val arcId: String? = null,
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
@SerialName("location-editor")
data class LocationEditorRoute(
    val storyId: String? = null,
    val locationId: String? = null,
) : NavKey

@Serializable
@SerialName("world-rule-editor")
data class WorldRuleEditorRoute(
    val storyId: String? = null,
    val worldRuleId: String? = null,
) : NavKey

@Serializable
@SerialName("culture-editor")
data class CultureEditorRoute(
    val storyId: String? = null,
    val cultureId: String? = null,
) : NavKey

@Serializable
@SerialName("event-editor")
data class EventEditorRoute(
    val storyId: String? = null,
    val eventId: String? = null,
) : NavKey

@Serializable
@SerialName("organization-editor")
data class OrganizationEditorRoute(
    val storyId: String? = null,
    val organizationId: String? = null,
) : NavKey

@Serializable
@SerialName("relationship-editor")
data class RelationshipEditorRoute(
    val storyId: String? = null,
    val relationshipId: String? = null,
) : NavKey

@Serializable
@SerialName("location-feature-editor")
data class LocationFeatureEditorRoute(
    val storyId: String? = null,
    val locationFeatureId: String? = null,
) : NavKey

@Serializable
@SerialName("artifact-editor")
data class ArtifactEditorRoute(
    val storyId: String? = null,
    val artifactId: String? = null,
) : NavKey

@Serializable
@SerialName("timeline-editor")
data class TimelineEditorRoute(
    val storyId: String? = null,
    val timelineEntryId: String? = null,
) : NavKey

@Serializable
@SerialName("story-chat")
data class StoryChatRoute(
    val storyId: String? = null,
) : NavKey
