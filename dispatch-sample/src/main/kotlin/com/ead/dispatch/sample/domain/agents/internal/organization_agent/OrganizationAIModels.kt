package com.ead.dispatch.sample.domain.agents.internal.organization_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class OrganizationStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): OrganizationStoryContext? {
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

            return OrganizationStoryContext(
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
enum class OrganizationAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class OrganizationAIRequest(
    val storyId: String,
    val prompt: String,
    val story: OrganizationStoryContext? = null,
    val existingOrganizationNames: List<String> = emptyList(),
    val mode: OrganizationAIMode = OrganizationAIMode.NORMAL,
)

@Serializable
data class OrganizationAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val purpose: String? = null,
    val structure: String? = null,
    val resources: List<String> = emptyList(),
    val publicImage: String? = null,
    val secrets: String? = null,
    val rivals: List<String> = emptyList(),
    val missingFields: List<String> = emptyList(),
)
