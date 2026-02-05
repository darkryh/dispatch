package com.ead.dispatch.sample.domain.agents.event_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class EventStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): EventStoryContext? {
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

            return EventStoryContext(
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
enum class EventAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class EventAIRequest(
    val storyId: String,
    val prompt: String,
    val story: EventStoryContext? = null,
    val existingEventNames: List<String> = emptyList(),
    val locationNames: List<String> = emptyList(),
    val organizationNames: List<String> = emptyList(),
    val mode: EventAIMode = EventAIMode.NORMAL,
)

@Serializable
data class EventAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val timeframe: String? = null,
    val location: String? = null,
    val causes: List<String> = emptyList(),
    val consequences: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val status: String? = null,
    val missingFields: List<String> = emptyList(),
)
