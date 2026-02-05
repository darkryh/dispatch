package com.ead.dispatch.sample.domain.agents.culture_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class CultureStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): CultureStoryContext? {
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

            return CultureStoryContext(
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
enum class CultureAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class CultureAIRequest(
    val storyId: String,
    val prompt: String,
    val story: CultureStoryContext? = null,
    val existingCultureNames: List<String> = emptyList(),
    val mode: CultureAIMode = CultureAIMode.NORMAL,
)

@Serializable
data class CultureAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val values: List<String> = emptyList(),
    val rituals: List<String> = emptyList(),
    val taboos: List<String> = emptyList(),
    val symbols: List<String> = emptyList(),
    val socialStructure: String? = null,
    val storyRole: String? = null,
    val missingFields: List<String> = emptyList(),
)
