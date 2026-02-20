package com.ead.dispatch.sample.domain.agents.internal.location_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class LocationStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): LocationStoryContext? {
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

            return LocationStoryContext(
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
enum class LocationAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class LocationAIRequest(
    val storyId: String,
    val prompt: String,
    val story: LocationStoryContext? = null,
    val existingLocationNames: List<String> = emptyList(),
    val mode: LocationAIMode = LocationAIMode.NORMAL,
)

@Serializable
data class LocationAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val atmosphere: String? = null,
    val function: String? = null,
    val access: String? = null,
    val risks: List<String> = emptyList(),
    val storyUse: String? = null,
    val missingFields: List<String> = emptyList(),
)
