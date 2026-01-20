package com.ead.dispatch.sample.domain.agents.chat_agent.tools.model

import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.StoryFactType
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
data class CreateFactRequest(
    val content: String,
    val factType: StoryFactType? = null,
)

@Serializable
data class UpdateFactRequest(
    val content: String? = null,
    val factType: StoryFactType? = null,
)
