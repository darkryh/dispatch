@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.type.ContentType
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftEditOperationRequest
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftEditType
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftPayload
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftProposalPayload
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftValidationPayload
import com.ead.dispatch.sample.domain.agents.tools.model.DraftValidationIssue
import com.ead.dispatch.sample.domain.agents.tools.model.DraftValidationSeverity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.ProposeChapterDraftEditRequest
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

class StoryDraftTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

    @Tool
    @LLMDescription("Get chapter draft text and checksum. Initializes an empty draft if none exists.")
    suspend fun getChapterDraft(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id")
        chapterId: String,
    ): ToolResult<QueryOutcome<ChapterDraftPayload>> {
        val chapter = chapterInStory(storyId, chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '$chapterId' not found.")
        val current = ensureCurrentDraft(storyId, chapter)
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Loaded chapter draft.",
            payload = current.toPayload(chapter.id),
        )
    }

    @Tool
    @LLMDescription("Replace the full draft text for a chapter. Use expectedChecksum to avoid stale overwrites.")
    suspend fun setChapterDraft(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id")
        chapterId: String,
        @LLMDescription("Full chapter draft text")
        text: String,
        @LLMDescription("Optional optimistic concurrency checksum from getChapterDraft")
        expectedChecksum: String? = null,
        @LLMDescription("Optional short reason for history/audit")
        note: String? = null,
    ): ToolResult<QueryOutcome<ChapterDraftPayload>> {
        val chapter = chapterInStory(storyId, chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '$chapterId' not found.")
        val current = ensureCurrentDraft(storyId, chapter)

        val normalizedExpected = expectedChecksum?.trim()
        if (!normalizedExpected.isNullOrBlank() && normalizedExpected != current.checksum) {
            return failure(
                "CONFLICT",
                "Draft checksum mismatch. Reload draft before applying changes.",
                details = "expected=$normalizedExpected, actual=${current.checksum}",
            )
        }

        val updated = persistDraftText(
            storyId = storyId,
            chapter = chapter,
            text = text,
            previous = current,
            note = note,
        )
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Updated chapter draft.",
            payload = updated.toPayload(chapter.id),
        )
    }

    @Tool
    @LLMDescription("Create a non-destructive edit proposal for chapter draft text. Does not apply changes yet.")
    suspend fun proposeChapterDraftEdit(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Edit proposal request")
        request: ProposeChapterDraftEditRequest,
    ): ToolResult<QueryOutcome<ChapterDraftProposalPayload>> {
        val chapter = chapterInStory(storyId, request.chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '${request.chapterId}' not found.")
        if (request.operations.isEmpty()) {
            return failure("MISSING_FIELD", "At least one edit operation is required.")
        }
        val current = ensureCurrentDraft(storyId, chapter)
        val expectedChecksum = request.expectedChecksum?.trim()
        if (!expectedChecksum.isNullOrBlank() && expectedChecksum != current.checksum) {
            return failure(
                "CONFLICT",
                "Draft checksum mismatch. Proposal must be based on latest draft.",
                details = "expected=$expectedChecksum, actual=${current.checksum}",
            )
        }

        val candidate = try {
            applyOperations(current.text, request.operations)
        } catch (error: IllegalArgumentException) {
            return failure(
                code = "INVALID_OPERATION",
                message = error.message ?: "Invalid draft edit operation.",
            )
        }
        if (candidate == current.text) {
            return failure("NO_CHANGES", "Proposal did not modify draft text.")
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val proposal = StoredDraftProposal(
            proposalId = UUID.randomUUID().toString(),
            storyId = storyId,
            chapterId = chapter.id,
            baseChecksum = current.checksum,
            candidateText = candidate,
            candidateChecksum = checksum(candidate),
            operationCount = request.operations.size,
            createdAt = now,
            note = request.note?.trim()?.takeIf { it.isNotEmpty() },
        )

        val saved = repository.putStoryMetadataValue(
            storyId = storyId,
            key = proposalKey(proposal.proposalId),
            value = json.encodeToString(StoredDraftProposal.serializer(), proposal),
        )
        if (!saved) return failure("NOT_FOUND", "Story record not found for session.")

        val payload = ChapterDraftProposalPayload(
            proposalId = proposal.proposalId,
            chapterId = proposal.chapterId,
            baseChecksum = proposal.baseChecksum,
            candidateChecksum = proposal.candidateChecksum,
            operationCount = proposal.operationCount,
            preview = compactPreview(proposal.candidateText),
            createdAt = proposal.createdAt,
            note = proposal.note,
        )
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Created chapter draft proposal.",
            payload = payload,
        )
    }

    @Tool
    @LLMDescription("Apply a previously proposed chapter draft edit by proposal id.")
    suspend fun applyChapterDraftProposal(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Proposal id from proposeChapterDraftEdit")
        proposalId: String,
        @LLMDescription("Set true to apply even if base checksum has changed")
        force: Boolean = false,
    ): ToolResult<QueryOutcome<ChapterDraftPayload>> {
        val proposal = loadProposal(storyId, proposalId)
            ?: return failure("NOT_FOUND", "Draft proposal '$proposalId' was not found.")
        if (proposal.storyId != storyId) {
            return failure("CONFLICT", "Proposal does not belong to this story.")
        }

        val chapter = chapterInStory(storyId, proposal.chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '${proposal.chapterId}' not found.")
        val current = ensureCurrentDraft(storyId, chapter)
        if (!force && proposal.baseChecksum != current.checksum) {
            return failure(
                "CONFLICT",
                "Draft changed since proposal was created. Re-propose or set force=true.",
                details = "proposalBase=${proposal.baseChecksum}, current=${current.checksum}",
            )
        }

        val applied = persistDraftText(
            storyId = storyId,
            chapter = chapter,
            text = proposal.candidateText,
            previous = current,
            note = proposal.note ?: "Applied proposal ${proposal.proposalId}",
        )

        repository.removeStoryMetadataValue(storyId, proposalKey(proposal.proposalId))
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Applied chapter draft proposal.",
            payload = applied.toPayload(chapter.id),
        )
    }

    @Tool
    @LLMDescription("Rollback chapter draft to a previous version. If versionId is omitted, uses latest history entry.")
    suspend fun rollbackChapterDraft(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id")
        chapterId: String,
        @LLMDescription("Optional version id from history")
        versionId: String? = null,
    ): ToolResult<QueryOutcome<ChapterDraftPayload>> {
        val chapter = chapterInStory(storyId, chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '$chapterId' not found.")
        val current = ensureCurrentDraft(storyId, chapter)
        val history = loadHistory(storyId, chapter.id)
        if (history.versions.isEmpty()) {
            return failure("NOT_FOUND", "No draft history available for rollback.")
        }

        val selected = if (versionId.isNullOrBlank()) {
            history.versions.first()
        } else {
            history.versions.firstOrNull { it.versionId == versionId }
                ?: return failure("NOT_FOUND", "Version '$versionId' was not found in history.")
        }

        val rolledBack = persistDraftText(
            storyId = storyId,
            chapter = chapter,
            text = selected.text,
            previous = current,
            note = "Rollback to ${selected.versionId}",
        )
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Rolled back chapter draft.",
            payload = rolledBack.toPayload(chapter.id),
        )
    }

    @Tool
    @LLMDescription("Validate chapter draft readiness for review with structural and target checks.")
    suspend fun validateChapterDraft(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id")
        chapterId: String,
    ): ToolResult<QueryOutcome<ChapterDraftValidationPayload>> {
        val chapter = chapterInStory(storyId, chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '$chapterId' not found.")
        val draft = ensureCurrentDraft(storyId, chapter)
        val scenes = repository.getScenesByChapter(chapter.id).sortedBy { it.number }
        val locations = repository.getLocationsByStory(storyId).associateBy { it.id }

        val issues = mutableListOf<DraftValidationIssue>()
        if (draft.text.isBlank()) {
            issues += DraftValidationIssue(
                code = "EMPTY_DRAFT",
                severity = DraftValidationSeverity.ERROR,
                message = "Chapter draft is empty.",
            )
        }

        val target = chapter.targetWordCount
        if (target != null && target > 0) {
            val minExpected = (target * 0.8).toLong()
            val maxExpected = (target * 1.2).toLong()
            if (draft.wordCount < minExpected) {
                issues += DraftValidationIssue(
                    code = "WORD_COUNT_LOW",
                    severity = DraftValidationSeverity.WARNING,
                    message = "Draft word count ${draft.wordCount} is below target range ($minExpected-$maxExpected).",
                )
            } else if (draft.wordCount > maxExpected) {
                issues += DraftValidationIssue(
                    code = "WORD_COUNT_HIGH",
                    severity = DraftValidationSeverity.WARNING,
                    message = "Draft word count ${draft.wordCount} is above target range ($minExpected-$maxExpected).",
                )
            }
        }

        val headerWindow = draft.text.take(500)
        if (chapter.title.isNotBlank() && !headerWindow.contains(chapter.title, ignoreCase = true)) {
            issues += DraftValidationIssue(
                code = "TITLE_MISSING",
                severity = DraftValidationSeverity.INFO,
                message = "Chapter title is not visible near the beginning of the draft.",
            )
        }

        scenes.forEach { scene ->
            val sceneMarker = Regex("""(?i)\bscene\s*${Regex.escape(scene.number.toString())}\b""")
            if (!sceneMarker.containsMatchIn(draft.text)) {
                issues += DraftValidationIssue(
                    code = "SCENE_MARKER_MISSING",
                    severity = DraftValidationSeverity.WARNING,
                    message = "Scene ${scene.number} marker was not found in the draft.",
                )
            }
            val sceneTitle = scene.title?.trim()
            if (!sceneTitle.isNullOrBlank() && !draft.text.contains(sceneTitle, ignoreCase = true)) {
                issues += DraftValidationIssue(
                    code = "SCENE_TITLE_MISSING",
                    severity = DraftValidationSeverity.INFO,
                    message = "Scene ${scene.number} title '$sceneTitle' was not found in draft text.",
                )
            }
            val locationId = scene.context?.locationId
            if (!locationId.isNullOrBlank() && locations[locationId] == null) {
                issues += DraftValidationIssue(
                    code = "SCENE_LOCATION_INVALID",
                    severity = DraftValidationSeverity.ERROR,
                    message = "Scene ${scene.number} references location '$locationId' that does not exist.",
                )
            }
        }

        val payload = ChapterDraftValidationPayload(
            chapterId = chapter.id,
            checksum = draft.checksum,
            wordCount = draft.wordCount,
            targetWordCount = target,
            issues = issues,
        )
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Validated chapter draft with ${issues.size} issue(s).",
            payload = payload,
        )
    }

    private suspend fun chapterInStory(
        storyId: String,
        chapterId: String,
    ): StoryChapterRecord? {
        val chapter = repository.getChapterById(chapterId) ?: return null
        val volume = repository.getVolumeById(chapter.volumeId) ?: return null
        return if (volume.storyId == storyId) chapter else null
    }

    private suspend fun ensureCurrentDraft(
        storyId: String,
        chapter: StoryChapterRecord,
    ): StoredDraftVersion {
        loadCurrentDraft(storyId, chapter.id)?.let { return it }

        val initialText = chapter.summary?.trim().orEmpty()
        val created = newVersion(initialText, note = "Initialize draft")
        saveCurrentDraft(storyId, chapter.id, created)
        syncChapterContentRecord(chapter, created)
        return created
    }

    private suspend fun persistDraftText(
        storyId: String,
        chapter: StoryChapterRecord,
        text: String,
        previous: StoredDraftVersion,
        note: String?,
    ): StoredDraftVersion {
        val next = newVersion(text, note)
        val history = loadHistory(storyId, chapter.id)
        val updatedHistory = StoredDraftHistory(
            versions = listOf(previous) +
                history.versions
                    .filterNot { it.versionId == previous.versionId }
                    .take(historyLimit - 1)
        )
        saveHistory(storyId, chapter.id, updatedHistory)
        saveCurrentDraft(storyId, chapter.id, next)
        syncChapterContentRecord(chapter, next)
        return next
    }

    private suspend fun syncChapterContentRecord(
        chapter: StoryChapterRecord,
        version: StoredDraftVersion,
    ) {
        val updatedChapter = chapter.copy(
            content = StoryChapterRecord.ChapterContent(
                ref = chapterDraftKey(chapter.id),
                type = ContentType.TEXT,
                checksum = version.checksum,
                updatedAt = version.updatedAt,
                range = chapter.content?.range,
                wordCount = version.wordCount,
            ),
            updatedAt = version.updatedAt,
        )
        repository.upsertChapter(updatedChapter)
    }

    private suspend fun loadCurrentDraft(
        storyId: String,
        chapterId: String,
    ): StoredDraftVersion? {
        val raw = repository.getStoryMetadataValue(storyId, chapterDraftKey(chapterId)) ?: return null
        return runCatching {
            json.decodeFromString(StoredDraftVersion.serializer(), raw)
        }.getOrNull()
    }

    private suspend fun saveCurrentDraft(
        storyId: String,
        chapterId: String,
        value: StoredDraftVersion,
    ) {
        repository.putStoryMetadataValue(
            storyId = storyId,
            key = chapterDraftKey(chapterId),
            value = json.encodeToString(StoredDraftVersion.serializer(), value),
        )
    }

    private suspend fun loadHistory(
        storyId: String,
        chapterId: String,
    ): StoredDraftHistory {
        val raw = repository.getStoryMetadataValue(storyId, chapterHistoryKey(chapterId)) ?: return StoredDraftHistory()
        return runCatching {
            json.decodeFromString(StoredDraftHistory.serializer(), raw)
        }.getOrDefault(StoredDraftHistory())
    }

    private suspend fun saveHistory(
        storyId: String,
        chapterId: String,
        history: StoredDraftHistory,
    ) {
        repository.putStoryMetadataValue(
            storyId = storyId,
            key = chapterHistoryKey(chapterId),
            value = json.encodeToString(StoredDraftHistory.serializer(), history),
        )
    }

    private suspend fun loadProposal(
        storyId: String,
        proposalId: String,
    ): StoredDraftProposal? {
        val key = proposalKey(proposalId)
        val raw = repository.getStoryMetadataValue(storyId, key) ?: return null
        return runCatching {
            json.decodeFromString(StoredDraftProposal.serializer(), raw)
        }.getOrNull()
    }

    private fun applyOperations(
        source: String,
        operations: List<ChapterDraftEditOperationRequest>,
    ): String {
        var text = source
        operations.forEachIndexed { index, operation ->
            text = applyOperation(text, operation, index)
        }
        return text
    }

    private fun applyOperation(
        source: String,
        operation: ChapterDraftEditOperationRequest,
        index: Int,
    ): String {
        val text = source
        val target = operation.target?.takeIf { it.isNotBlank() }
        val insertText = operation.text.orEmpty()

        fun fail(message: String): Nothing {
            throw IllegalArgumentException("Operation ${index + 1}: $message")
        }

        return when (operation.type) {
            ChapterDraftEditType.PREPEND -> insertText + text

            ChapterDraftEditType.APPEND -> text + insertText

            ChapterDraftEditType.REPLACE -> {
                if (target == null) fail("REPLACE requires a non-empty target.")
                val positions = findMatches(text, target)
                if (positions.isEmpty()) fail("Target not found for REPLACE.")
                if (!operation.all && positions.size > 1) {
                    fail("Target is ambiguous for REPLACE. Use a more specific selector or all=true.")
                }
                if (operation.all) {
                    text.replace(target, insertText)
                } else {
                    val start = positions.first()
                    text.replaceRange(start, start + target.length, insertText)
                }
            }

            ChapterDraftEditType.INSERT_BEFORE -> {
                if (target == null) fail("INSERT_BEFORE requires a non-empty target.")
                val positions = findMatches(text, target)
                if (positions.isEmpty()) fail("Target not found for INSERT_BEFORE.")
                if (!operation.all && positions.size > 1) {
                    fail("Target is ambiguous for INSERT_BEFORE. Use a more specific selector or all=true.")
                }
                if (operation.all) {
                    positions.sortedDescending().fold(text) { acc, start ->
                        acc.replaceRange(start, start, insertText)
                    }
                } else {
                    val start = positions.first()
                    text.replaceRange(start, start, insertText)
                }
            }

            ChapterDraftEditType.INSERT_AFTER -> {
                if (target == null) fail("INSERT_AFTER requires a non-empty target.")
                val positions = findMatches(text, target)
                if (positions.isEmpty()) fail("Target not found for INSERT_AFTER.")
                if (!operation.all && positions.size > 1) {
                    fail("Target is ambiguous for INSERT_AFTER. Use a more specific selector or all=true.")
                }
                if (operation.all) {
                    positions.sortedDescending().fold(text) { acc, start ->
                        acc.replaceRange(start + target.length, start + target.length, insertText)
                    }
                } else {
                    val start = positions.first()
                    text.replaceRange(start + target.length, start + target.length, insertText)
                }
            }

            ChapterDraftEditType.DELETE -> {
                if (target == null) fail("DELETE requires a non-empty target.")
                val positions = findMatches(text, target)
                if (positions.isEmpty()) fail("Target not found for DELETE.")
                if (!operation.all && positions.size > 1) {
                    fail("Target is ambiguous for DELETE. Use a more specific selector or all=true.")
                }
                if (operation.all) {
                    text.replace(target, "")
                } else {
                    val start = positions.first()
                    text.removeRange(start, start + target.length)
                }
            }
        }
    }

    private fun findMatches(text: String, target: String): List<Int> {
        if (target.isEmpty()) return emptyList()
        val positions = mutableListOf<Int>()
        var startIndex = 0
        while (true) {
            val index = text.indexOf(target, startIndex)
            if (index < 0) break
            positions += index
            startIndex = index + target.length
        }
        return positions
    }

    private fun compactPreview(text: String, maxChars: Int = 280): String {
        val normalized = text.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        if (normalized.length <= maxChars) return normalized
        return normalized.take(maxChars) + "..."
    }

    private fun checksum(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun wordCount(text: String): Long =
        Regex("""\S+""")
            .findAll(text)
            .count()
            .toLong()

    private fun newVersion(text: String, note: String?): StoredDraftVersion {
        val now = Clock.System.now().toEpochMilliseconds()
        val normalizedText = text.trimEnd()
        return StoredDraftVersion(
            versionId = UUID.randomUUID().toString(),
            text = normalizedText,
            checksum = checksum(normalizedText),
            wordCount = wordCount(normalizedText),
            updatedAt = now,
            note = note?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun StoredDraftVersion.toPayload(chapterId: String): ChapterDraftPayload =
        ChapterDraftPayload(
            chapterId = chapterId,
            text = text,
            checksum = checksum,
            wordCount = wordCount,
            updatedAt = updatedAt,
            versionId = versionId,
        )

    private fun chapterDraftKey(chapterId: String): String = "story:draft:chapter:$chapterId"

    private fun chapterHistoryKey(chapterId: String): String = "story:draft:history:$chapterId"

    private fun proposalKey(proposalId: String): String = "story:draft:proposal:$proposalId"

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        const val historyLimit = 20
    }
}

@Serializable
private data class StoredDraftVersion(
    val versionId: String,
    val text: String,
    val checksum: String,
    val wordCount: Long,
    val updatedAt: Long,
    val note: String? = null,
)

@Serializable
private data class StoredDraftHistory(
    val versions: List<StoredDraftVersion> = emptyList(),
)

@Serializable
private data class StoredDraftProposal(
    val proposalId: String,
    val storyId: String,
    val chapterId: String,
    val baseChecksum: String,
    val candidateText: String,
    val candidateChecksum: String,
    val operationCount: Int,
    val createdAt: Long,
    val note: String? = null,
)
