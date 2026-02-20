package com.ead.dispatch.sample.domain.agents.story_agent.memory.service

import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySnapshot
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummarizer
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummary
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryContinuitySnapshot
import java.util.UUID
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class StoryContinuityMemoryService(
    private val repository: StructuredIndexRepository,
    private val summarizer: StoryChapterMemorySummarizer? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private companion object {
        const val MAX_RECENT_CHAPTERS_AGGREGATE = 8L
        const val MAX_ACTIVE_THREADS = 8
        const val MAX_RECENT_NEW_FACTS = 8
        const val MAX_RECENT_RESOLVED_THREADS = 6
        const val MAX_CONTINUITY_WARNINGS = 6
        const val MAX_PROMPT_CHAPTERS = 4L
        const val MAX_PROMPT_KEY_BEATS = 3
        const val MAX_PROMPT_ENTITIES = 4
        const val MAX_PROMPT_NEW_FACTS = 3
        const val MAX_PROMPT_RESOLVED_THREADS = 2
        const val MAX_PROMPT_OPEN_THREADS = 3
        const val MAX_PROMPT_CONTINUITY_RISKS = 2
        const val MAX_PROMPT_WARNINGS = 2
    }

    suspend fun refreshFromApprovedChapter(
        storyId: String,
        chapter: StoryChapterRecord,
        approvedText: String,
        approvedChecksum: String,
    ) {
        val story = repository.getStoryById(storyId) ?: return
        val scenes = repository.getScenesByChapter(chapter.id).sortedBy { it.number }
        val now = Clock.System.now().toEpochMilliseconds()

        val baseKeyBeats = buildKeyBeats(chapter, scenes)
        val entities = extractEntities(storyId)
        val llmSummary = buildSummary(chapter, approvedText, baseKeyBeats)

        if (!llmSummary.isUsable) {
            repository.upsertStoryMemoryRetryQueue(
                StructuredIndexRepository.StoryMemoryRetryQueueState(
                    id = "retry-${chapter.id}-${approvedChecksum.hashCode()}",
                    storyId = storyId,
                    chapterId = chapter.id,
                    approvedChecksum = approvedChecksum,
                    failureReason = "summarizer_unusable",
                    attemptCount = 1,
                    nextAttemptAt = now + (5 * 60 * 1000),
                    lastError = "Summarizer output is unusable or empty.",
                    updatedAt = now,
                )
            )
            return
        }

        repository.upsertStoryChapterMemory(
            StructuredIndexRepository.StoryChapterMemoryState(
                chapterId = chapter.id,
                storyId = storyId,
                approvedChecksum = approvedChecksum,
                summaryShort = llmSummary.summaryShort,
                summaryDelta = llmSummary.summaryDelta,
                keyBeatsJson = encodeList(baseKeyBeats),
                newFactsJson = encodeList(llmSummary.newFacts),
                resolvedThreadsJson = encodeList(llmSummary.resolvedThreads),
                openThreadsJson = encodeList(llmSummary.unresolvedThreads),
                continuityRisksJson = encodeList(llmSummary.continuityRisks),
                warningsJson = encodeList(llmSummary.warnings),
                entitiesJson = encodeList(entities),
                summarizerConfidence = normalizeConfidence(llmSummary.confidence),
                summarizerUsable = llmSummary.isUsable,
                summarizerModel = "story-main",
                summarizerRunId = "sum-${chapter.id}-${now}",
                pov = story.styleProfile?.pov,
                tense = story.styleProfile?.tense,
                updatedAt = now,
            )
        )

        repository.replaceStoryChapterMemoryItems(
            chapterId = chapter.id,
            items = buildMemoryItems(
                chapter = chapter,
                storyId = storyId,
                keyBeats = baseKeyBeats,
                summary = llmSummary,
                createdAt = now,
            ),
        )
        repository.clearStoryMemoryRetryQueue(chapter.id, approvedChecksum)

        val recent = repository.listStoryChapterMemoryByStoryId(storyId, limit = MAX_RECENT_CHAPTERS_AGGREGATE)
        val rollingSummary = recent
            .sortedBy { it.updatedAt }
            .joinToString(separator = " ") { it.summaryShort }
            .compact(900)
        val rollingDelta = recent
            .sortedBy { it.updatedAt }
            .joinToString(separator = " ") { it.summaryDelta }
            .compact(700)
        val activeThreads = recent
            .flatMap { decodeList(it.openThreadsJson) + decodeList(it.continuityRisksJson) }
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_ACTIVE_THREADS)
        val recentNewFacts = recent
            .flatMap { decodeList(it.newFactsJson) }
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_RECENT_NEW_FACTS)
        val recentResolved = recent
            .flatMap { decodeList(it.resolvedThreadsJson) }
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_RECENT_RESOLVED_THREADS)
        val warnings = recent
            .flatMap { decodeList(it.warningsJson) }
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(MAX_CONTINUITY_WARNINGS)
        val chapterIds = recent.map { it.chapterId }

        repository.upsertStoryContinuityMemory(
            StructuredIndexRepository.StoryContinuityMemoryState(
                storyId = storyId,
                rollingDelta = rollingDelta,
                rollingSummary = rollingSummary,
                activeThreadsJson = encodeList(activeThreads),
                recentNewFactsJson = encodeList(recentNewFacts),
                recentResolvedThreadsJson = encodeList(recentResolved),
                continuityWarningsJson = encodeList(warnings),
                lastChapterIdsJson = encodeList(chapterIds),
                packetModel = "continuity-packet-v2",
                packetGeneratedAt = now,
                updatedAt = now,
            )
        )
    }

    suspend fun loadForPrompt(storyId: String): StoryContinuitySnapshot? {
        val continuity = repository.getStoryContinuityMemoryByStoryId(storyId) ?: return null
        val recentRows = repository.listStoryChapterMemoryByStoryId(storyId, limit = MAX_PROMPT_CHAPTERS)
        val recentSnapshots = recentRows.map { row ->
            StoryChapterMemorySnapshot(
                chapterId = row.chapterId,
                summaryShort = row.summaryShort,
                summaryDelta = row.summaryDelta,
                keyBeats = decodeList(row.keyBeatsJson).take(MAX_PROMPT_KEY_BEATS),
                entities = decodeList(row.entitiesJson).take(MAX_PROMPT_ENTITIES),
                newFacts = decodeList(row.newFactsJson).take(MAX_PROMPT_NEW_FACTS),
                resolvedThreads = decodeList(row.resolvedThreadsJson).take(MAX_PROMPT_RESOLVED_THREADS),
                unresolvedThreads = decodeList(row.openThreadsJson).take(MAX_PROMPT_OPEN_THREADS),
                continuityRisks = decodeList(row.continuityRisksJson).take(MAX_PROMPT_CONTINUITY_RISKS),
                warnings = decodeList(row.warningsJson).take(MAX_PROMPT_WARNINGS),
                summarizerConfidence = row.summarizerConfidence,
                summarizerUsable = row.summarizerUsable,
                pov = row.pov,
                tense = row.tense,
                updatedAt = row.updatedAt,
            )
        }

        val snapshot = StoryContinuitySnapshot(
            rollingDelta = continuity.rollingDelta,
            rollingSummary = continuity.rollingSummary,
            activeThreads = decodeList(continuity.activeThreadsJson),
            recentNewFacts = decodeList(continuity.recentNewFactsJson),
            resolvedThreads = decodeList(continuity.recentResolvedThreadsJson),
            continuityWarnings = decodeList(continuity.continuityWarningsJson),
            recentChapters = recentSnapshots,
        )

        return snapshot
    }

    private suspend fun buildSummary(
        chapter: StoryChapterRecord,
        approvedText: String,
        keyBeats: List<String>,
    ): StoryChapterMemorySummary {
        if (summarizer == null) {
            val summaryDelta = summarizeDraftText(chapter, approvedText)
            return StoryChapterMemorySummary(
                summaryShort = summaryDelta.compact(280),
                summaryDelta = summaryDelta.compact(220),
                confidence = "MEDIUM",
                isUsable = summaryDelta.isNotBlank(),
            )
        }

        val result = summarizer.summarize(chapter, approvedText, keyBeats)
        if (result == null) {
            return StoryChapterMemorySummary(
                summaryShort = "",
                summaryDelta = "",
                confidence = "LOW",
                isUsable = false,
            )
        }
        return result
    }

    private fun summarizeDraftText(
        chapter: StoryChapterRecord,
        text: String,
    ): String {
        val normalized = text.compact(280)
        if (normalized.isNotBlank()) return normalized
        return chapter.summary?.compact(280).orEmpty().ifBlank { "Chapter ${chapter.number}: no approved summary yet." }
    }

    private fun buildKeyBeats(
        chapter: StoryChapterRecord,
        scenes: List<StorySceneRecord>,
    ): List<String> {
        val beats = mutableListOf<String>()
        beats += chapter.keyEvents
        scenes.forEach { scene ->
            val summary = scene.summary?.trim().orEmpty()
            if (summary.isNotEmpty()) beats += "Scene ${scene.number}: $summary"
        }
        return beats.map { it.compact(120) }.distinct()
    }

    private fun buildMemoryItems(
        chapter: StoryChapterRecord,
        storyId: String,
        keyBeats: List<String>,
        summary: StoryChapterMemorySummary,
        createdAt: Long,
    ): List<StructuredIndexRepository.StoryChapterMemoryItemState> {
        fun toItems(
            kind: String,
            values: List<String>,
            status: String = "ACTIVE",
        ): List<StructuredIndexRepository.StoryChapterMemoryItemState> = values
            .mapIndexedNotNull { index, raw ->
                val value = raw.trim()
                if (value.isEmpty()) return@mapIndexedNotNull null
                StructuredIndexRepository.StoryChapterMemoryItemState(
                    id = "mem-${UUID.randomUUID()}",
                    chapterId = chapter.id,
                    storyId = storyId,
                    kind = kind,
                    value = value,
                    position = index.toLong(),
                    sourceChapterId = chapter.id,
                    sourceVolumeId = chapter.volumeId,
                    status = status,
                    confidence = normalizeConfidence(summary.confidence),
                    createdAt = createdAt,
                )
            }

        return buildList {
            addAll(toItems(kind = "KEY_BEAT", values = keyBeats))
            addAll(toItems(kind = "NEW_FACT", values = summary.newFacts))
            addAll(toItems(kind = "RESOLVED_THREAD", values = summary.resolvedThreads, status = "RESOLVED"))
            addAll(toItems(kind = "OPEN_THREAD", values = summary.unresolvedThreads))
            addAll(toItems(kind = "CONTINUITY_RISK", values = summary.continuityRisks))
            addAll(toItems(kind = "WARNING", values = summary.warnings))
        }
    }

    private suspend fun extractEntities(storyId: String): List<String> {
        val characters = repository.getStoryCharacters(storyId).map { "character:${it.name}" }
        val locations = repository.getLocationsByStory(storyId).map { "location:${it.profile.name}" }
        val arcs = repository.getArcsByStory(storyId).map { "arc:${it.title}" }
        return (characters + locations + arcs).distinct()
    }

    private fun normalizeConfidence(raw: String): String =
        when (raw.trim().uppercase()) {
            "HIGH" -> "HIGH"
            "MEDIUM" -> "MEDIUM"
            else -> "LOW"
        }

    private fun decodeList(raw: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrElse { emptyList() }

    private fun encodeList(values: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), values)

}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}
