package com.ead.dispatch.sample.domain.agents.tools.model

import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.ContentType
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
data class CreateVolumeRequest(
    val number: Long,
    val title: String,
    val summary: String? = null,
    val targetWordCount: Long? = null,
    val status: ContentStatus? = null,
    val notes: String? = null,
    val keyEvents: List<String>? = null,
)

@Serializable
data class UpdateVolumeRequest(
    val title: String? = null,
    val summary: String? = null,
    val targetWordCount: Long? = null,
    val status: ContentStatus? = null,
    val notes: String? = null,
    val keyEvents: List<String>? = null,
)

@Serializable
data class ChapterContentRequest(
    val ref: String? = null,
    val type: ContentType? = null,
    val checksum: String? = null,
    val updatedAt: Long? = null,
    val range: String? = null,
    val wordCount: Long? = null,
)

@Serializable
data class CreateChapterRequest(
    val volumeId: String,
    val number: Long,
    val title: String,
    val summary: String? = null,
    val content: ChapterContentRequest? = null,
    val keyEvents: List<String>? = null,
    val targetWordCount: Long? = null,
    val status: ContentStatus? = null,
)

@Serializable
data class UpdateChapterRequest(
    val title: String? = null,
    val summary: String? = null,
    val content: ChapterContentRequest? = null,
    val keyEvents: List<String>? = null,
    val targetWordCount: Long? = null,
    val status: ContentStatus? = null,
)

@Serializable
data class SceneContextRequest(
    val range: String? = null,
    val pov: String? = null,
    val emotionalBeat: String? = null,
    val locationId: String? = null,
    val timeSpan: String? = null,
)

@Serializable
data class CreateSceneRequest(
    val chapterId: String,
    val number: Long,
    val title: String? = null,
    val summary: String? = null,
    val context: SceneContextRequest? = null,
    val keyEvents: List<String>? = null,
    val status: ContentStatus? = null,
)

@Serializable
data class UpdateSceneRequest(
    val title: String? = null,
    val summary: String? = null,
    val context: SceneContextRequest? = null,
    val keyEvents: List<String>? = null,
    val status: ContentStatus? = null,
)

@Serializable
enum class ChapterDraftEditType {
    REPLACE,
    INSERT_BEFORE,
    INSERT_AFTER,
    DELETE,
    PREPEND,
    APPEND,
}

@Serializable
data class ChapterDraftEditOperationRequest(
    val type: ChapterDraftEditType,
    val target: String? = null,
    val text: String? = null,
    val all: Boolean = false,
)

@Serializable
data class ProposeChapterDraftEditRequest(
    val chapterId: String,
    val operations: List<ChapterDraftEditOperationRequest>,
    val expectedChecksum: String? = null,
    val note: String? = null,
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

@Serializable
data class ChoiceOptionRequest(
    val id: String? = null,
    val label: String,
)

@Serializable
data class UserChoiceRequest(
    val promptId: String? = null,
    val question: String,
    val options: List<ChoiceOptionRequest>,
    val allowCustom: Boolean = true,
    val minChoices: Int = 1,
    val maxChoices: Int = 1,
    val customPlaceholder: String? = null,
)

@Serializable
data class ChoiceOptionPayload(
    val id: String,
    val label: String,
)

@Serializable
data class UserChoicePayload(
    val type: String = "user_choice",
    val promptId: String,
    val question: String,
    val options: List<ChoiceOptionPayload>,
    val allowCustom: Boolean,
    val minChoices: Int,
    val maxChoices: Int,
    val customPlaceholder: String,
)
