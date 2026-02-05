package com.ead.dispatch.sample.domain.agents.timeline_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class TimelineStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): TimelineStoryContext? {
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

            return TimelineStoryContext(
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
enum class TimelineAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class TimelineAIRequest(
    val storyId: String,
    val prompt: String,
    val constraints: String? = null,
    val story: TimelineStoryContext? = null,
    val existingTimelineTitles: List<String> = emptyList(),
    val mode: TimelineAIMode = TimelineAIMode.NORMAL,
)

@Serializable
data class TimelineAIDraft(
    val title: String = "",
    val summary: String = "",
    val description: String? = null,
    val time: String? = null,
    val orderIndex: Long? = null,
    val impact: List<String> = emptyList(),
    val certainty: String? = null,
    val missingFields: List<String> = emptyList(),
)
