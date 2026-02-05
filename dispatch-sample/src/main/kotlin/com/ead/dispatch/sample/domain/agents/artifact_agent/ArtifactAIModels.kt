package com.ead.dispatch.sample.domain.agents.artifact_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class ArtifactStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): ArtifactStoryContext? {
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

            return ArtifactStoryContext(
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
enum class ArtifactAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class ArtifactAIRequest(
    val storyId: String,
    val prompt: String,
    val story: ArtifactStoryContext? = null,
    val existingArtifactNames: List<String> = emptyList(),
    val characterNames: List<String> = emptyList(),
    val organizationNames: List<String> = emptyList(),
    val locationNames: List<String> = emptyList(),
    val mode: ArtifactAIMode = ArtifactAIMode.NORMAL,
)

@Serializable
data class ArtifactAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val origin: String? = null,
    val powers: List<String> = emptyList(),
    val costs: List<String> = emptyList(),
    val limitations: List<String> = emptyList(),
    val owner: String? = null,
    val ownerType: String? = null,
    val location: String? = null,
    val missingFields: List<String> = emptyList(),
)
