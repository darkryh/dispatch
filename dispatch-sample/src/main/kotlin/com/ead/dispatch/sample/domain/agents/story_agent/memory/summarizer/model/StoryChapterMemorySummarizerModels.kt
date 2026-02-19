package com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model

import kotlinx.serialization.Serializable

data class StoryChapterMemorySummarizeRequest(
    val chapterNumber: Long,
    val chapterTitle: String,
    val chapterSummary: String?,
    val keyBeats: List<String>,
    val approvedTextExcerpt: String,
)

@Serializable
data class StoryChapterMemorySummaryDraft(
    val summaryDelta: String = "",
    val newFacts: List<String> = emptyList(),
    val resolvedThreads: List<String> = emptyList(),
    val openThreads: List<String> = emptyList(),
    val continuityRisks: List<String> = emptyList(),
)
