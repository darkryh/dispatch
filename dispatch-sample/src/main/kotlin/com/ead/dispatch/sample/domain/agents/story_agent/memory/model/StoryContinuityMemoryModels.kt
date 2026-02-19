package com.ead.dispatch.sample.domain.agents.story_agent.memory.model

import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import kotlinx.serialization.Serializable

@Serializable
data class StoryChapterMemorySnapshot(
    val chapterId: String,
    val summaryShort: String,
    val keyBeats: List<String>,
    val entities: List<String>,
    val unresolvedThreads: List<String>,
    val pov: String? = null,
    val tense: String? = null,
    val updatedAt: Long,
)

@Serializable
data class StoryContinuitySnapshot(
    val rollingSummary: String,
    val activeThreads: List<String>,
    val continuityWarnings: List<String>,
    val recentChapters: List<StoryChapterMemorySnapshot>,
)

data class StoryChapterMemorySummary(
    val summaryShort: String,
    val unresolvedThreads: List<String> = emptyList(),
)

fun interface StoryChapterMemorySummarizer {
    suspend fun summarize(
        chapter: StoryChapterRecord,
        approvedText: String,
        keyBeats: List<String>,
    ): StoryChapterMemorySummary?
}
