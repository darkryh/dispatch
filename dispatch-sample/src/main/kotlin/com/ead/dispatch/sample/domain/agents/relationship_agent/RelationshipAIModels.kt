package com.ead.dispatch.sample.domain.agents.relationship_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class RelationshipStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): RelationshipStoryContext? {
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

            return RelationshipStoryContext(
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
enum class RelationshipAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class RelationshipAIRequest(
    val storyId: String,
    val prompt: String,
    val constraints: String? = null,
    val story: RelationshipStoryContext? = null,
    val characterNames: List<String> = emptyList(),
    val organizationNames: List<String> = emptyList(),
    val locationNames: List<String> = emptyList(),
    val mode: RelationshipAIMode = RelationshipAIMode.NORMAL,
)

@Serializable
data class RelationshipAIDraft(
    val subjectName: String? = null,
    val subjectType: String? = null,
    val objectName: String? = null,
    val objectType: String? = null,
    val relation: String = "",
    val summary: String = "",
    val history: String? = null,
    val tension: String? = null,
    val currentStatus: String? = null,
    val missingFields: List<String> = emptyList(),
)
