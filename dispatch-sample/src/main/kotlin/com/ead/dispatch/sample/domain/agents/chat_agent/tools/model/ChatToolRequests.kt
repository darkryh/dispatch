package com.ead.dispatch.sample.domain.agents.chat_agent.tools.model

import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import kotlinx.serialization.Serializable

@Serializable
data class StoryUpsertRequest(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val status: ContentStatus? = null,
    val styleProfile: StoryStyleProfileRequest? = null,
    val styleRefs: List<String>? = null,
    val emotionalBeats: List<String>? = null,
)

@Serializable
data class StoryStyleProfileRequest(
    val logline: String? = null,
    val theme: String? = null,
    val tone: String? = null,
    val stakes: String? = null,
    val pov: String? = null,
    val tense: String? = null,
    val targetAudience: String? = null,
    val pacing: String? = null,
)

@Serializable
data class CreateCharacterRequest(
    val name: String,
    val description: String? = null,
    val traits: List<String>? = null,
    val roles: List<String>? = null,
    val goal: String? = null,
    val motivation: String? = null,
    val flaw: String? = null,
    val temperament: String? = null,
    val age: String? = null,
    val pronouns: String? = null,
    val occupation: String? = null,
    val backstory: String? = null,
    val voice: String? = null,
    val internalConflict: String? = null,
    val quirks: List<String>? = null,
    val physical: CharacterPhysicalProfileRequest? = null,
)

@Serializable
data class UpdateCharacterRequest(
    val name: String? = null,
    val description: String? = null,
    val traits: List<String>? = null,
    val roles: List<String>? = null,
    val goal: String? = null,
    val motivation: String? = null,
    val flaw: String? = null,
    val temperament: String? = null,
    val age: String? = null,
    val pronouns: String? = null,
    val occupation: String? = null,
    val backstory: String? = null,
    val voice: String? = null,
    val internalConflict: String? = null,
    val quirks: List<String>? = null,
    val physical: CharacterPhysicalProfileRequest? = null,
)

@Serializable
data class CharacterPhysicalProfileRequest(
    val appearance: String? = null,
    val height: String? = null,
    val build: String? = null,
    val hair: String? = null,
    val eyes: String? = null,
    val skinTone: String? = null,
    val distinguishingMarks: String? = null,
    val styleNotes: String? = null,
)

@Serializable
data class CreateLocationRequest(
    val name: String,
    val description: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class UpdateLocationRequest(
    val name: String? = null,
    val description: String? = null,
    val tags: List<String>? = null,
)

@Serializable
data class CreateArcRequest(
    val title: String,
    val summary: String? = null,
    val scopeType: ArcScope? = null,
    val scopeId: String? = null,
    val status: ContentStatus? = null,
)

@Serializable
data class UpdateArcRequest(
    val title: String? = null,
    val summary: String? = null,
    val scopeType: ArcScope? = null,
    val scopeId: String? = null,
    val status: ContentStatus? = null,
)

@Serializable
data class CreateWorldRuleRequest(
    val title: String,
    val description: String? = null,
)

@Serializable
data class UpdateWorldRuleRequest(
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateCultureRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UpdateCultureRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateEventRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UpdateEventRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateOrganizationRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UpdateOrganizationRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class CreateRelationshipRequest(
    val subjectId: String,
    val subjectType: String,
    val objectId: String,
    val objectType: String,
    val relation: String,
    val notes: String? = null,
)

@Serializable
data class UpdateRelationshipRequest(
    val subjectId: String? = null,
    val subjectType: String? = null,
    val objectId: String? = null,
    val objectType: String? = null,
    val relation: String? = null,
    val notes: String? = null,
)

@Serializable
data class CreateLocationFeatureRequest(
    val name: String,
    val description: String? = null,
    val locationId: String? = null,
)

@Serializable
data class UpdateLocationFeatureRequest(
    val name: String? = null,
    val description: String? = null,
    val locationId: String? = null,
)

@Serializable
data class CreateArtifactRequest(
    val name: String,
    val description: String? = null,
    val ownerId: String? = null,
    val ownerType: String? = null,
    val locationId: String? = null,
)

@Serializable
data class UpdateArtifactRequest(
    val name: String? = null,
    val description: String? = null,
    val ownerId: String? = null,
    val ownerType: String? = null,
    val locationId: String? = null,
)

@Serializable
data class CreateTimelineEntryRequest(
    val title: String,
    val description: String? = null,
    val orderIndex: Long,
)

@Serializable
data class UpdateTimelineEntryRequest(
    val title: String? = null,
    val description: String? = null,
    val orderIndex: Long? = null,
)
