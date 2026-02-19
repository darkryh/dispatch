package com.ead.dispatch.sample.domain.agents.story_agent.memory.service

import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySnapshot
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryChapterMemorySummarizer
import com.ead.dispatch.sample.domain.agents.story_agent.memory.model.StoryContinuitySnapshot
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class StoryContinuityMemoryService(
    private val repository: StructuredIndexRepository,
    private val summarizer: StoryChapterMemorySummarizer? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

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
        var summaryShort = summarizeDraftText(chapter, approvedText)
        var summaryDelta = summaryShort
        val keyBeats = baseKeyBeats.toMutableList()
        var unresolved = keyBeats.takeLast(2)
        val additionalWarnings = mutableListOf<String>()
        summarizer?.summarize(chapter, approvedText, keyBeats)?.let { llmSummary ->
            summaryShort = llmSummary.summaryShort.ifBlank { summaryShort }
            summaryDelta = llmSummary.summaryDelta.ifBlank { summaryDelta }
            if (llmSummary.unresolvedThreads.isNotEmpty()) {
                unresolved = llmSummary.unresolvedThreads
            }
            if (llmSummary.newFacts.isNotEmpty()) {
                keyBeats += llmSummary.newFacts.map { fact -> "Fact: ${fact.compact(100)}" }
            }
            if (llmSummary.resolvedThreads.isNotEmpty()) {
                keyBeats += llmSummary.resolvedThreads.map { thread -> "Resolved: ${thread.compact(100)}" }
            }
            if (llmSummary.continuityRisks.isNotEmpty()) {
                unresolved += llmSummary.continuityRisks.map { risk -> "Risk: ${risk.compact(100)}" }
                additionalWarnings += llmSummary.continuityRisks.map { risk -> "Continuity risk: ${risk.compact(100)}" }
            }
        }

        repository.upsertStoryChapterMemory(
            StructuredIndexRepository.StoryChapterMemoryState(
                chapterId = chapter.id,
                storyId = storyId,
                approvedChecksum = approvedChecksum,
                summaryShort = summaryShort,
                keyBeatsJson = json.encodeToString(ListSerializer(String.serializer()), keyBeats.distinct().take(10)),
                entitiesJson = json.encodeToString(ListSerializer(String.serializer()), entities),
                unresolvedThreadsJson = json.encodeToString(ListSerializer(String.serializer()), unresolved.distinct().take(5)),
                pov = story.styleProfile?.pov,
                tense = story.styleProfile?.tense,
                updatedAt = now,
            )
        )

        val recent = repository.listStoryChapterMemoryByStoryId(storyId, limit = 6)

        val rollingSummary = recent
            .sortedBy { it.updatedAt }
            .joinToString(separator = " ") { it.summaryShort }
            .compact(700)
        val activeThreads = recent
            .flatMap { decodeList(it.unresolvedThreadsJson) }
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
            .take(5)
        val warnings = (buildWarnings(recent) + additionalWarnings).distinct().take(4)
        val chapterIds = recent.map { it.chapterId }

        repository.upsertStoryContinuityMemory(
            StructuredIndexRepository.StoryContinuityMemoryState(
                storyId = storyId,
                rollingSummary = rollingSummary,
                activeThreadsJson = json.encodeToString(ListSerializer(String.serializer()), activeThreads),
                continuityWarningsJson = json.encodeToString(ListSerializer(String.serializer()), warnings),
                lastChapterIdsJson = json.encodeToString(ListSerializer(String.serializer()), chapterIds),
                updatedAt = now,
            )
        )

    }

    suspend fun loadForPrompt(
        storyId: String,
        maxChars: Int = 1500,
    ): StoryContinuitySnapshot? {
        val continuity = repository.getStoryContinuityMemoryByStoryId(storyId) ?: return null
        val recentRows = repository.listStoryChapterMemoryByStoryId(storyId, limit = 4)
        val recentSnapshots = recentRows.map { row ->
            StoryChapterMemorySnapshot(
                chapterId = row.chapterId,
                summaryShort = row.summaryShort,
                summaryDelta = row.summaryShort,
                keyBeats = decodeList(row.keyBeatsJson).take(3),
                entities = decodeList(row.entitiesJson).take(4),
                newFacts = decodeList(row.keyBeatsJson)
                    .filter { it.startsWith("Fact:") }
                    .map { it.removePrefix("Fact:").trim() }
                    .take(2),
                resolvedThreads = decodeList(row.keyBeatsJson)
                    .filter { it.startsWith("Resolved:") }
                    .map { it.removePrefix("Resolved:").trim() }
                    .take(1),
                unresolvedThreads = decodeList(row.unresolvedThreadsJson).take(2),
                continuityRisks = decodeList(row.unresolvedThreadsJson)
                    .filter { it.startsWith("Risk:") }
                    .map { it.removePrefix("Risk:").trim() }
                    .take(1),
                pov = row.pov,
                tense = row.tense,
                updatedAt = row.updatedAt,
            )
        }
        val rollingDelta = recentSnapshots
            .sortedBy { it.updatedAt }
            .joinToString(" ") { it.summaryDelta }
            .compact(400)
        val recentNewFacts = recentSnapshots
            .flatMap { it.newFacts }
            .distinct()
            .take(6)
        val resolvedThreads = recentSnapshots
            .flatMap { it.resolvedThreads }
            .distinct()
            .take(4)
        return trimToBudget(
            StoryContinuitySnapshot(
                rollingDelta = rollingDelta,
                rollingSummary = continuity.rollingSummary,
                activeThreads = decodeList(continuity.activeThreadsJson),
                recentNewFacts = recentNewFacts,
                resolvedThreads = resolvedThreads,
                continuityWarnings = decodeList(continuity.continuityWarningsJson),
                recentChapters = recentSnapshots,
            ),
            maxChars = maxChars,
        )
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
        return beats.map { it.compact(120) }.distinct().take(8)
    }

    private suspend fun extractEntities(storyId: String): List<String> {
        val characters = repository.getStoryCharacters(storyId).take(3).map { "character:${it.name}" }
        val locations = repository.getLocationsByStory(storyId).take(2).map { "location:${it.profile.name}" }
        val arcs = repository.getArcsByStory(storyId).take(2).map { "arc:${it.title}" }
        return (characters + locations + arcs).distinct()
    }

    private fun buildWarnings(
        recent: List<StructuredIndexRepository.StoryChapterMemoryState>,
    ): List<String> {
        if (recent.isEmpty()) return emptyList()
        val duplicateSummaries = recent
            .groupBy { it.summaryShort }
            .filterValues { it.size > 1 }
            .keys
        return if (duplicateSummaries.isEmpty()) {
            emptyList()
        } else {
            listOf("Potential repetitive continuity notes detected.")
        }
    }

    private fun decodeList(raw: String): List<String> =
        runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrElse { emptyList() }

    private fun trimToBudget(
        snapshot: StoryContinuitySnapshot,
        maxChars: Int,
    ): StoryContinuitySnapshot {
        var budget = maxChars.coerceAtLeast(200)
        val rollingDelta = snapshot.rollingDelta.compact((budget * 25) / 100).also { budget -= it.length }
        val rolling = snapshot.rollingSummary.compact((budget * 45) / 100).also { budget -= it.length }
        val threads = snapshot.activeThreads
            .map { it.compact(90) }
            .scanWithinBudget(budget / 3)
            .also { used -> budget -= used.sumOf { it.length } }
        val facts = snapshot.recentNewFacts
            .map { it.compact(80) }
            .scanWithinBudget(budget / 4)
            .also { used -> budget -= used.sumOf { it.length } }
        val resolved = snapshot.resolvedThreads
            .map { it.compact(80) }
            .scanWithinBudget(budget / 6)
            .also { used -> budget -= used.sumOf { it.length } }
        val chapters = snapshot.recentChapters
            .map { chapter ->
                chapter.copy(
                    summaryShort = chapter.summaryShort.compact(140),
                    summaryDelta = chapter.summaryDelta.compact(110),
                    keyBeats = chapter.keyBeats.map { it.compact(80) }.take(2),
                    entities = chapter.entities.take(3),
                    newFacts = chapter.newFacts.map { it.compact(70) }.take(1),
                    resolvedThreads = chapter.resolvedThreads.map { it.compact(70) }.take(1),
                    unresolvedThreads = chapter.unresolvedThreads.map { it.compact(80) }.take(1),
                    continuityRisks = chapter.continuityRisks.map { it.compact(70) }.take(1),
                )
            }
            .scanChaptersWithinBudget((budget * 8) / 10)
        val warnings = snapshot.continuityWarnings
            .map { it.compact(90) }
            .take(2)
        return StoryContinuitySnapshot(
            rollingDelta = rollingDelta,
            rollingSummary = rolling,
            activeThreads = threads,
            recentNewFacts = facts,
            resolvedThreads = resolved,
            continuityWarnings = warnings,
            recentChapters = chapters,
        )
    }
}

private fun String.compact(maxChars: Int): String {
    val normalized = replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    return if (normalized.length <= maxChars) normalized else normalized.take(maxChars) + "..."
}

private fun List<String>.scanWithinBudget(maxChars: Int): List<String> {
    var used = 0
    val result = mutableListOf<String>()
    for (item in this) {
        if (used + item.length > maxChars) break
        result += item
        used += item.length
    }
    return result
}

private fun List<StoryChapterMemorySnapshot>.scanChaptersWithinBudget(
    maxChars: Int,
): List<StoryChapterMemorySnapshot> {
    var used = 0
    val result = mutableListOf<StoryChapterMemorySnapshot>()
    for (item in this) {
        val textCost = item.summaryShort.length +
            item.keyBeats.sumOf { it.length } +
            item.entities.sumOf { it.length } +
            item.unresolvedThreads.sumOf { it.length }
        if (used + textCost > maxChars) break
        result += item
        used += textCost
    }
    return result
}
