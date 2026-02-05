package com.ead.dispatch.sample.domain.agents.character_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class CharacterStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): CharacterStoryContext? {
            if (story == null) return null
            val style = story.styleProfile
            val styleNotes = listOfNotNull(
                style?.logline?.takeIf { it.isNotBlank() }?.let { "logline=$it" },
                style?.theme?.takeIf { it.isNotBlank() }?.let { "theme=$it" },
                style?.tone?.takeIf { it.isNotBlank() }?.let { "tone=$it" },
                style?.stakes?.takeIf { it.isNotBlank() }?.let { "stakes=$it" },
                style?.pov?.takeIf { it.isNotBlank() }?.let { "pov=$it" },
                style?.tense?.takeIf { it.isNotBlank() }?.let { "tense=$it" },
                style?.targetAudience?.takeIf { it.isNotBlank() }?.let { "audience=$it" },
                style?.pacing?.takeIf { it.isNotBlank() }?.let { "pacing=$it" },
            ).joinToString(" · ").takeIf { it.isNotBlank() }

            return CharacterStoryContext(
                title = story.title?.takeIf { it.isNotBlank() },
                genre = story.genre?.takeIf { it.isNotBlank() },
                setting = story.setting?.takeIf { it.isNotBlank() },
                plotOutline = story.plotOutline?.takeIf { it.isNotBlank() },
                styleNotes = styleNotes,
            )
        }
    }
}

@Serializable
enum class CharacterAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class CharacterAIRequest(
    val storyId: String,
    val prompt: String,
    val story: CharacterStoryContext? = null,
    val existingCharacterNames: List<String> = emptyList(),
    val mode: CharacterAIMode = CharacterAIMode.NORMAL,
)

@Serializable
data class CharacterAIDraft(
    val name: String = "",
    val description: String? = null,
    val roles: List<String> = emptyList(),
    val goal: String? = null,
    val motivation: String? = null,
    val flaw: String? = null,
    val internalConflict: String? = null,
    val temperament: String? = null,
    val age: String? = null,
    val pronouns: String? = null,
    val occupation: String? = null,
    val backstory: String? = null,
    val voice: String? = null,
    val traits: List<String> = emptyList(),
    val quirks: List<String> = emptyList(),
    val physical: PhysicalDraft? = null,
    val summary: String = "",
    val missingFields: List<String> = emptyList(),
) {
    @Serializable
    data class PhysicalDraft(
        val appearance: String? = null,
        val height: String? = null,
        val build: String? = null,
        val hair: String? = null,
        val eyes: String? = null,
        val skinTone: String? = null,
        val distinguishingMarks: String? = null,
        val styleNotes: String? = null,
    )
}
