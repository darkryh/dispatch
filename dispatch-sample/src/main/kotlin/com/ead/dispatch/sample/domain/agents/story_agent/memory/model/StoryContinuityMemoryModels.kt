package com.ead.dispatch.sample.domain.agents.story_agent.memory.model

import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import kotlinx.serialization.Serializable

@Serializable
data class StoryChapterMemorySnapshot(
    val chapterId: String,
    val summaryShort: String,
    val summaryDelta: String = "",
    val keyBeats: List<String>,
    val entities: List<String>,
    val newFacts: List<String> = emptyList(),
    val resolvedThreads: List<String> = emptyList(),
    val unresolvedThreads: List<String>,
    val continuityRisks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val summarizerConfidence: String = "LOW",
    val summarizerUsable: Boolean = false,
    val pov: String? = null,
    val tense: String? = null,
    val updatedAt: Long,
)

@Serializable
data class StoryContinuitySnapshot(
    val rollingDelta: String = "",
    val rollingSummary: String,
    val activeThreads: List<String>,
    val recentNewFacts: List<String> = emptyList(),
    val resolvedThreads: List<String> = emptyList(),
    val continuityWarnings: List<String>,
    val recentChapters: List<StoryChapterMemorySnapshot>,
)

data class StoryChapterMemorySummary(
    val summaryShort: String,
    val summaryDelta: String = "",
    val newFacts: List<String> = emptyList(),
    val resolvedThreads: List<String> = emptyList(),
    val unresolvedThreads: List<String> = emptyList(),
    val continuityRisks: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val confidence: String = "LOW",
    val isUsable: Boolean = false,
)

fun interface StoryChapterMemorySummarizer {
    suspend fun summarize(
        chapter: StoryChapterRecord,
        approvedText: String,
        keyBeats: List<String>,
    ): StoryChapterMemorySummary?
}
