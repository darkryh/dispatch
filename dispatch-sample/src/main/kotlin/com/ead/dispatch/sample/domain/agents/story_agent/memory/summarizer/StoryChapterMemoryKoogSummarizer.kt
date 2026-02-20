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
    private companion object {
        const val MAX_INPUT_KEY_BEATS = 10
        const val MAX_NEW_FACTS = 3
        const val MAX_RESOLVED_THREADS = 2
        const val MAX_OPEN_THREADS = 3
        const val MAX_CONTINUITY_RISKS = 2
        const val MAX_WARNINGS = 2
    }

    override suspend fun summarize(
        chapter: StoryChapterRecord,
        approvedText: String,
        keyBeats: List<String>,
    ): StoryChapterMemorySummary? {
        val request = StoryChapterMemorySummarizeRequest(
            chapterNumber = chapter.number,
            chapterTitle = chapter.title,
            chapterSummary = chapter.summary,
            keyBeats = keyBeats.take(MAX_INPUT_KEY_BEATS),
            approvedTextExcerpt = approvedText.compact(2_200)
        )

        val agent = AIAgent<StoryChapterMemorySummarizeRequest, Result<StructuredResponse<StoryChapterMemorySummaryDraft>>, >(
            promptExecutor = AIProvider.Sync.storyExecutor,
            llmModel = AIProvider.Story.main,
            strategy = strategy<StoryChapterMemorySummarizeRequest, Result<StructuredResponse<StoryChapterMemorySummaryDraft>>>("story-memory-summarizer") {
                val summarizeNode by nodeSummarizeStoryMemory()
                edge(nodeStart forwardTo summarizeNode)
                edge(summarizeNode forwardTo nodeFinish transformed { it })
            },
            responseProcessor = null,
            maxIterations = 4,
            temperature = 0.4,
            id = "story-memory-summarizer",
        )
        val result = runCatching { agent.run(request).getOrThrow().data }.getOrNull() ?: return null
        val summaryDelta = result.summaryDelta.trim()
        val normalizedConfidence = result.confidence.trim().uppercase().ifBlank { "LOW" }
        val isUsable = result.isUsable && summaryDelta.isNotEmpty()
        if (!isUsable) {
            return StoryChapterMemorySummary(
                summaryShort = "",
                summaryDelta = "",
                confidence = normalizedConfidence,
                isUsable = false,
            )
        }

        return StoryChapterMemorySummary(
            summaryShort = summaryDelta.compact(280),
            summaryDelta = summaryDelta.compact(220),
            newFacts = result.newFacts
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(MAX_NEW_FACTS),
            resolvedThreads = result.resolvedThreads
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(MAX_RESOLVED_THREADS),
            unresolvedThreads = result.openThreads
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(MAX_OPEN_THREADS),
            continuityRisks = result.continuityRisks
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(MAX_CONTINUITY_RISKS),
            warnings = result.warnings
                .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                .distinct()
                .take(MAX_WARNINGS),
            confidence = normalizedConfidence,
            isUsable = true,
        )
    }
}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}
