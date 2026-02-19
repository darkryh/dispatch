package com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.structure.StructuredResponse
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummarizer
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummary
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model.StoryChapterMemorySummarizeRequest
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.model.StoryChapterMemorySummaryDraft
import com.ead.dispatch.sample.domain.agents.story_agent.memory.summarizer.node.nodeSummarizeStoryMemory

class StoryChapterMemoryKoogSummarizer : StoryChapterMemorySummarizer {
    override suspend fun summarize(
        chapter: StoryChapterRecord,
        approvedText: String,
        keyBeats: List<String>,
    ): StoryChapterMemorySummary? {
        val request = StoryChapterMemorySummarizeRequest(
            chapterNumber = chapter.number,
            chapterTitle = chapter.title,
            chapterSummary = chapter.summary,
            keyBeats = keyBeats.take(10),
            approvedTextExcerpt = approvedText.compact(2_200),
        )
        val agent = AIAgent<StoryChapterMemorySummarizeRequest, Result<StructuredResponse<StoryChapterMemorySummaryDraft>>>(
            promptExecutor = AIProvider.Sync.storyExecutor,
            llmModel = AIProvider.Story.main,
            toolRegistry = ToolRegistry {},
            strategy = strategy<StoryChapterMemorySummarizeRequest, Result<StructuredResponse<StoryChapterMemorySummaryDraft>>>("story-memory-summarizer") {
                val summarizeNode by nodeSummarizeStoryMemory()
                edge(nodeStart forwardTo summarizeNode)
                edge(summarizeNode forwardTo nodeFinish transformed { it })
            },
            maxIterations = 4,
            temperature = 0.4,
            id = "story-memory-summarizer",
        )
        val result = runCatching { agent.run(request).getOrThrow().data }.getOrNull() ?: return null
        val summary = result.summaryShort.trim().takeIf { it.isNotEmpty() } ?: return null
        return StoryChapterMemorySummary(
            summaryShort = summary.compact(280),
            unresolvedThreads = result.unresolvedThreads
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(3),
        )
    }
}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}
