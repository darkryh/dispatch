package com.ead.dispatch.sample.domain.agents.world_rule_agent

import com.ead.dispatch.sample.data.db.entities.StoryRecord
import kotlinx.serialization.Serializable

@Serializable
data class WorldRuleStoryContext(
    val title: String? = null,
    val genre: String? = null,
    val setting: String? = null,
    val plotOutline: String? = null,
    val styleNotes: String? = null,
) {
    companion object {
        fun fromStory(story: StoryRecord?): WorldRuleStoryContext? {
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

            return WorldRuleStoryContext(
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
enum class WorldRuleAIMode {
    NORMAL,
    CREATIVE,
}

@Serializable
data class WorldRuleAIRequest(
    val storyId: String,
    val prompt: String,
    val constraints: String? = null,
    val story: WorldRuleStoryContext? = null,
    val existingRuleTitles: List<String> = emptyList(),
    val mode: WorldRuleAIMode = WorldRuleAIMode.NORMAL,
)

@Serializable
data class WorldRuleAIDraft(
    val title: String = "",
    val summary: String = "",
    val rule: String? = null,
    val scope: String? = null,
    val implications: List<String> = emptyList(),
    val exceptions: List<String> = emptyList(),
    val storyImpact: String? = null,
    val missingFields: List<String> = emptyList(),
)
