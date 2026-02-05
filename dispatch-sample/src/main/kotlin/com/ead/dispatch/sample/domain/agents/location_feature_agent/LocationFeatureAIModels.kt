package com.ead.dispatch.sample.domain.agents.location_feature_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class LocationFeatureStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): LocationFeatureStoryContext? {
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

            return LocationFeatureStoryContext(
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
enum class LocationFeatureAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class LocationFeatureAIRequest(
    val storyId: String,
    val prompt: String,
    val constraints: String? = null,
    val story: LocationFeatureStoryContext? = null,
    val existingFeatureNames: List<String> = emptyList(),
    val locationNames: List<String> = emptyList(),
    val mode: LocationFeatureAIMode = LocationFeatureAIMode.NORMAL,
)

@Serializable
data class LocationFeatureAIDraft(
    val name: String = "",
    val summary: String = "",
    val description: String? = null,
    val featureType: String? = null,
    val function: String? = null,
    val risks: List<String> = emptyList(),
    val relatedLocation: String? = null,
    val missingFields: List<String> = emptyList(),
)
