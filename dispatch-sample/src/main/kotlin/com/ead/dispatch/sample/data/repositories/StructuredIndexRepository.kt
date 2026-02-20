package com.ead.dispatch.sample.data.repositories

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ead.dispatch.sample.data.db.entities.*
import com.ead.dispatch.sample.data.db.type.*
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.EmbeddingMode
import com.ead.dispatch.sample.domain.embedding.EmbeddingTextBuilder
import com.ead.dispatch.sample.domain.model.story.OverflowList
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.domain.model.story.StoryModeContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class StructuredIndexRepository(
    private val databaseRuntime: DatabaseRuntime,
    private val embeddingIndexService: EmbeddingIndexService,
) {
    data class StoryDraftCurrentState(
        val chapterId: String,
        val contentRef: String,
        val checksum: String,
        val wordCount: Long,
        val updatedAt: Long,
        val updatedBy: String? = null,
    )

    data class StoryDraftVersionState(
        val id: String,
        val chapterId: String,
        val contentRef: String,
        val checksum: String,
        val wordCount: Long,
        val createdAt: Long,
        val note: String? = null,
        val source: String? = null,
    )

    data class StoryDraftProposalState(
        val id: String,
        val chapterId: String,
        val baseChecksum: String,
        val baseContentRef: String,
        val candidateContentRef: String,
        val candidateChecksum: String,
        val operationCount: Long,
        val status: String,
        val createdAt: Long,
        val decidedAt: Long? = null,
        val note: String? = null,
    )

    data class StoryDraftPreviewState(
        val chapterId: String,
        val status: String,
        val proposalId: String? = null,
        val beforeContentRef: String,
        val afterContentRef: String,
        val pendingRangesJson: String,
        val createdAt: Long,
        val updatedAt: Long,
    )

    data class StoryChapterMemoryState(
        val chapterId: String,
        val storyId: String,
        val approvedChecksum: String,
        val summaryShort: String,
        val summaryDelta: String,
        val keyBeatsJson: String,
        val newFactsJson: String,
        val resolvedThreadsJson: String,
        val openThreadsJson: String,
        val continuityRisksJson: String,
        val warningsJson: String,
        val entitiesJson: String,
        val summarizerConfidence: String,
        val summarizerUsable: Boolean,
        val summarizerModel: String? = null,
        val summarizerRunId: String? = null,
        val pov: String? = null,
        val tense: String? = null,
        val updatedAt: Long,
    )

    data class StoryChapterMemoryItemState(
        val id: String,
        val chapterId: String,
        val storyId: String,
        val kind: String,
        val value: String,
        val position: Long,
        val sourceChapterId: String? = null,
        val sourceVolumeId: String? = null,
        val status: String,
        val confidence: String,
        val createdAt: Long,
    )

    data class StoryContinuityMemoryState(
        val storyId: String,
        val rollingDelta: String,
        val rollingSummary: String,
        val activeThreadsJson: String,
        val recentNewFactsJson: String,
        val recentResolvedThreadsJson: String,
        val continuityWarningsJson: String,
        val lastChapterIdsJson: String,
        val packetModel: String? = null,
        val packetGeneratedAt: Long? = null,
        val updatedAt: Long,
    )

    data class StoryMemoryRetryQueueState(
        val id: String,
        val storyId: String,
        val chapterId: String,
        val approvedChecksum: String,
        val failureReason: String,
        val attemptCount: Long,
        val nextAttemptAt: Long? = null,
        val lastError: String? = null,
        val updatedAt: Long,
    )

    data class RelationshipIntegrityResult(
        val isValid: Boolean,
        val normalizedSubjectType: String? = null,
        val normalizedObjectType: String? = null,
        val error: String? = null,
    )

    private data class LimitedSlice<T>(
        val items: List<T>,
        val overflow: Int,
    )

    private fun <T> limitLatest(
        items: List<T>,
        limit: Int,
        selector: (T) -> Long,
    ): LimitedSlice<T> {
        if (items.isEmpty()) return LimitedSlice(emptyList(), 0)
        val sorted = items.sortedByDescending(selector)
        val limited = if (sorted.size > limit) sorted.take(limit) else sorted
        val overflow = (sorted.size - limited.size).coerceAtLeast(0)
        return LimitedSlice(limited, overflow)
    }

    private companion object {
        const val CHAT_CONTEXT_CHARACTER_LIMIT = 5
        const val CHAT_CONTEXT_LOCATION_LIMIT = 5
        const val CHAT_CONTEXT_ARC_LIMIT = 4
        const val CHAT_CONTEXT_WORLD_RULE_LIMIT = 3
        const val CHAT_CONTEXT_CULTURE_LIMIT = 3
        const val CHAT_CONTEXT_EVENT_LIMIT = 3
        const val CHAT_CONTEXT_ORGANIZATION_LIMIT = 3
        const val CHAT_CONTEXT_RELATIONSHIP_LIMIT = 3
        const val CHAT_CONTEXT_LOCATION_FEATURE_LIMIT = 3
        const val CHAT_CONTEXT_ARTIFACT_LIMIT = 3
        const val CHAT_CONTEXT_TIMELINE_LIMIT = 3
        val RELATIONSHIP_ENTITY_TYPES = setOf(
            "CHARACTER",
            "LOCATION",
            "ORGANIZATION",
            "ARTIFACT",
            "EVENT",
            "CULTURE",
            "WORLD_RULE",
            "TIMELINE",
        )
    }

    private val database = databaseRuntime.database
    private val queries = databaseRuntime.queries
    private val coroutineDispatcher: CoroutineDispatcher = databaseRuntime.dispatcher

    private suspend inline fun <T> query(crossinline block: () -> T): T =
        databaseRuntime.query { block() }

    private fun normalizeRelationshipEntityType(rawType: String): String? =
        rawType.trim().uppercase().takeIf { it in RELATIONSHIP_ENTITY_TYPES }

    private suspend fun storyEntityExists(
        storyId: String,
        entityType: String,
        entityId: String,
    ): Boolean {
        val normalizedId = entityId.trim()
        if (normalizedId.isBlank()) return false
        return query {
            when (entityType) {
                "CHARACTER" -> queries.selectCharacterIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "LOCATION" -> queries.selectLocationIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "ORGANIZATION" -> queries.selectOrganizationIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "ARTIFACT" -> queries.selectArtifactIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "EVENT" -> queries.selectEventIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "CULTURE" -> queries.selectCultureIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "WORLD_RULE" -> queries.selectWorldRuleIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                "TIMELINE" -> queries.selectTimelineEntryIdByStoryAndId(story_id = storyId, id = normalizedId).executeAsOneOrNull() != null
                else -> false
            }
        }
    }

    suspend fun validateRelationshipEndpoints(
        storyId: String,
        subjectId: String,
        subjectType: String,
        objectId: String,
        objectType: String,
    ): RelationshipIntegrityResult {
        val normalizedSubjectId = subjectId.trim()
        if (normalizedSubjectId.isBlank()) {
            return RelationshipIntegrityResult(
                isValid = false,
                error = "Relationship subject id is required.",
            )
        }

        val normalizedObjectId = objectId.trim()
        if (normalizedObjectId.isBlank()) {
            return RelationshipIntegrityResult(
                isValid = false,
                error = "Relationship object id is required.",
            )
        }

        val normalizedSubjectType = normalizeRelationshipEntityType(subjectType)
            ?: return RelationshipIntegrityResult(
                isValid = false,
                error = "Unsupported subject type '$subjectType'.",
            )
        val normalizedObjectType = normalizeRelationshipEntityType(objectType)
            ?: return RelationshipIntegrityResult(
                isValid = false,
                error = "Unsupported object type '$objectType'.",
            )

        val subjectExists = storyEntityExists(
            storyId = storyId,
            entityType = normalizedSubjectType,
            entityId = normalizedSubjectId,
        )
        if (!subjectExists) {
            return RelationshipIntegrityResult(
                isValid = false,
                error = "Subject not found in story: $normalizedSubjectType:$normalizedSubjectId",
            )
        }

        val objectExists = storyEntityExists(
            storyId = storyId,
            entityType = normalizedObjectType,
            entityId = normalizedObjectId,
        )
        if (!objectExists) {
            return RelationshipIntegrityResult(
                isValid = false,
                error = "Object not found in story: $normalizedObjectType:$normalizedObjectId",
            )
        }

        return RelationshipIntegrityResult(
            isValid = true,
            normalizedSubjectType = normalizedSubjectType,
            normalizedObjectType = normalizedObjectType,
        )
    }

    private suspend fun normalizeAndValidateRelationshipRecord(
        record: StoryRelationshipRecord,
    ): StoryRelationshipRecord {
        val relation = record.relation.trim()
        require(relation.isNotBlank()) { "Relationship label is required." }

        val validation = validateRelationshipEndpoints(
            storyId = record.storyId,
            subjectId = record.subjectId,
            subjectType = record.subjectType,
            objectId = record.objectId,
            objectType = record.objectType,
        )
        require(validation.isValid) { validation.error ?: "Invalid relationship references." }

        return record.copy(
            subjectId = record.subjectId.trim(),
            subjectType = validation.normalizedSubjectType ?: record.subjectType.trim().uppercase(),
            objectId = record.objectId.trim(),
            objectType = validation.normalizedObjectType ?: record.objectType.trim().uppercase(),
            relation = relation,
            notes = record.notes?.trim()?.takeIf { it.isNotBlank() },
        )
    }

    private fun getSessionMetadata(sessionId: String): Map<String, String> =
        queries.selectSessionMetadataBySessionId(sessionId) { _, key, value ->
            key to value
        }.executeAsList().associate { it.first to it.second }

    private fun getStoryStyleRefs(storyId: String): List<String> =
        queries.selectStoryStyleRefsByStoryId(storyId) { _, _, value ->
            value
        }.executeAsList()

    private fun getStoryEmotionalBeats(storyId: String): List<String> =
        queries.selectStoryEmotionalBeatsByStoryId(storyId) { _, _, value ->
            value
        }.executeAsList()

    private fun getCharacterTraits(characterId: String): List<String> =
        queries.selectStoryCharacterTraitsByCharacterId(characterId) { _, _, value ->
            value
        }.executeAsList()

    private fun getCharacterRoles(characterId: String): List<String> =
        queries.selectStoryCharacterRolesByCharacterId(characterId) { _, _, value ->
            value
        }.executeAsList()

    private fun getCharacterQuirks(characterId: String): List<String> =
        queries.selectStoryCharacterQuirksByCharacterId(characterId) { _, _, value ->
            value
        }.executeAsList()

    private suspend fun upsertEntityEmbedding(
        storyId: String,
        sourceRef: String,
        text: String,
        mode: EmbeddingMode = EmbeddingMode.CHAT,
    ) {
        val embeddingService = embeddingIndexService
        val normalized = text.trim()
        if (normalized.isBlank()) return

        val checksum = embeddingService.checksum(normalized)
        val existing = query {
            queries.selectRagDocumentsByStoryAndSourceRef(storyId, sourceRef) { docId, sessionId, storyIdValue, volumeId, chapterId, chunkIndex, sourceType, sourceRefValue, checksumValue, createdAt ->
                RagDocumentRecord(
                    docId = docId,
                    sessionId = sessionId,
                    storyId = storyIdValue,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    chunkIndex = chunkIndex,
                    sourceType = RagSourceType.fromDb(sourceType),
                    sourceRef = sourceRefValue,
                    checksum = checksumValue,
                    createdAt = createdAt,
                )
            }.executeAsList()
        }

        if (existing.any { it.checksum == checksum }) return

        existing.forEach { record ->
            embeddingService.delete(mode, record.sessionId, record.docId)
        }
        query {
            queries.deleteRagDocumentsByStoryAndSourceRef(storyId, sourceRef)
        }

        val sessionId = getStoryById(storyId)?.sessionId ?: storyId
        val docId = embeddingService.store(mode, sessionId, normalized) ?: return
        query {
            queries.insertRagDocument(
                doc_id = docId,
                session_id = sessionId,
                story_id = storyId,
                volume_id = null,
                chapter_id = null,
                chunk_index = 0,
                source_type = RagSourceType.NOTE.name,
                source_ref = sourceRef,
                checksum = checksum,
                created_at = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun deleteEntityEmbedding(
        storyId: String,
        sourceRef: String,
        mode: EmbeddingMode = EmbeddingMode.CHAT,
    ) {
        val embeddingService = embeddingIndexService
        val existing = query {
            queries.selectRagDocumentsByStoryAndSourceRef(storyId, sourceRef) { docId, sessionId, storyIdValue, volumeId, chapterId, chunkIndex, sourceType, sourceRefValue, checksumValue, createdAt ->
                RagDocumentRecord(
                    docId = docId,
                    sessionId = sessionId,
                    storyId = storyIdValue,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    chunkIndex = chunkIndex,
                    sourceType = RagSourceType.fromDb(sourceType),
                    sourceRef = sourceRefValue,
                    checksum = checksumValue,
                    createdAt = createdAt,
                )
            }.executeAsList()
        }

        existing.forEach { record ->
            embeddingService.delete(mode, record.sessionId, record.docId)
        }
        query {
            queries.deleteRagDocumentsByStoryAndSourceRef(storyId, sourceRef)
        }
    }

    private suspend fun deleteEntityEmbedding(
        sourceRef: String,
        mode: EmbeddingMode = EmbeddingMode.CHAT,
    ) {
        val embeddingService = embeddingIndexService
        val existing = query {
            queries.selectRagDocumentsBySourceRef(sourceRef) { docId, sessionId, storyIdValue, volumeId, chapterId, chunkIndex, sourceType, sourceRefValue, checksumValue, createdAt ->
                RagDocumentRecord(
                    docId = docId,
                    sessionId = sessionId,
                    storyId = storyIdValue,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    chunkIndex = chunkIndex,
                    sourceType = RagSourceType.fromDb(sourceType),
                    sourceRef = sourceRefValue,
                    checksum = checksumValue,
                    createdAt = createdAt,
                )
            }.executeAsList()
        }

        existing.forEach { record ->
            embeddingService.delete(mode, record.sessionId, record.docId)
        }
        query {
            queries.deleteRagDocumentsBySourceRef(sourceRef)
        }
    }

    private fun toPhysicalProfile(
        appearance: String?,
        height: String?,
        build: String?,
        hair: String?,
        eyes: String?,
        skinTone: String?,
        distinguishingMarks: String?,
        styleNotes: String?,
    ): StoryCharacterRecord.PhysicalProfile? {
        if (
            appearance == null &&
            height == null &&
            build == null &&
            hair == null &&
            eyes == null &&
            skinTone == null &&
            distinguishingMarks == null &&
            styleNotes == null
        ) {
            return null
        }
        return StoryCharacterRecord.PhysicalProfile(
            appearance = appearance,
            height = height,
            build = build,
            hair = hair,
            eyes = eyes,
            skinTone = skinTone,
            distinguishingMarks = distinguishingMarks,
            styleNotes = styleNotes,
        )
    }

    private fun getVolumeKeyEvents(volumeId: String): List<String> =
        queries.selectStoryVolumeKeyEventsByVolumeId(volumeId) { _, _, value ->
            value
        }.executeAsList()

    private fun getChapterKeyEvents(chapterId: String): List<String> =
        queries.selectStoryChapterKeyEventsByChapterId(chapterId) { _, _, value ->
            value
        }.executeAsList()

    private fun getSceneKeyEvents(sceneId: String): List<String> =
        queries.selectStorySceneKeyEventsBySceneId(sceneId) { _, _, value ->
            value
        }.executeAsList()

    private fun getLocationTags(locationId: String): List<String> =
        queries.selectStoryLocationTagsByLocationId(locationId) { _, _, value ->
            value
        }.executeAsList()

    private fun toStoryStyleProfile(
        logline: String?,
        theme: String?,
        tone: String?,
        stakes: String?,
        pov: String?,
        tense: String?,
        targetAudience: String?,
        pacing: String?,
    ): StoryRecord.StoryStyleProfile? {
        if (
            logline == null &&
            theme == null &&
            tone == null &&
            stakes == null &&
            pov == null &&
            tense == null &&
            targetAudience == null &&
            pacing == null
        ) {
            return null
        }
        return StoryRecord.StoryStyleProfile(
            logline = logline,
            theme = theme,
            tone = tone,
            stakes = stakes,
            pov = pov,
            tense = tense,
            targetAudience = targetAudience,
            pacing = pacing,
        )
    }

    private fun toVolumePlan(
        summary: String?,
        targetWordCount: Long?,
        status: ContentStatus?,
        notes: String?,
    ): StoryVolumeRecord.VolumePlan? {
        if (summary == null && targetWordCount == null && status == null && notes == null) {
            return null
        }
        return StoryVolumeRecord.VolumePlan(
            summary = summary,
            targetWordCount = targetWordCount,
            status = status,
            notes = notes,
        )
    }

    private fun toChapterContent(
        contentRef: String?,
        contentType: ContentType?,
        contentChecksum: String?,
        contentUpdatedAt: Long?,
        contentRange: String?,
        wordCount: Long?,
    ): StoryChapterRecord.ChapterContent? {
        if (
            contentRef == null &&
            contentType == null &&
            contentChecksum == null &&
            contentUpdatedAt == null &&
            contentRange == null &&
            wordCount == null
        ) {
            return null
        }
        return StoryChapterRecord.ChapterContent(
            ref = contentRef,
            type = contentType,
            checksum = contentChecksum,
            updatedAt = contentUpdatedAt,
            range = contentRange,
            wordCount = wordCount,
        )
    }

    private fun toSceneContext(
        contentRange: String?,
        pov: String?,
        emotionalBeat: String?,
        locationId: String?,
        timeSpan: String?,
    ): StorySceneRecord.SceneContext? {
        if (
            contentRange == null &&
            pov == null &&
            emotionalBeat == null &&
            locationId == null &&
            timeSpan == null
        ) {
            return null
        }
        return StorySceneRecord.SceneContext(
            range = contentRange,
            pov = pov,
            emotionalBeat = emotionalBeat,
            locationId = locationId,
            timeSpan = timeSpan,
        )
    }

    suspend fun upsertSession(record: SessionRecord) = query {
        database.transaction {
            val existing = queries.selectSessionById(record.id).executeAsOneOrNull()
            if (existing == null) {
                queries.insertSession(
                    id = record.id,
                    title = record.profile.title,
                    mode = record.profile.mode.name,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                    message_count = record.stats.messageCount,
                )
            } else {
                queries.updateSession(
                    id = record.id,
                    title = record.profile.title,
                    mode = record.profile.mode.name,
                    updated_at = record.updatedAt,
                    message_count = record.stats.messageCount,
                )
            }
            queries.deleteSessionMetadataBySessionId(record.id)
            record.metadata.entries.sortedBy { it.key }.forEach { (key, value) ->
                queries.insertSessionMetadata(
                    session_id = record.id,
                    key = key,
                    value = value,
                )
            }
        }
    }

    suspend fun getSessions(): List<SessionRecord> = query {
        val sessions = queries.selectSessions { id, title, mode, createdAt, updatedAt, messageCount ->
            SessionRecord(
                id = id,
                profile = SessionRecord.SessionProfile(
                    title = title,
                    mode = SessionMode.fromDb(mode),
                ),
                createdAt = createdAt,
                updatedAt = updatedAt,
                stats = SessionRecord.SessionStats(
                    messageCount = messageCount,
                ),
                metadata = emptyMap(),
            )
        }.executeAsList()

        sessions.map { session ->
            session.copy(metadata = getSessionMetadata(session.id))
        }
    }

    suspend fun getSessionById(sessionId: String): SessionRecord? = query {
        val session = queries.selectSessionById(sessionId) { id, title, mode, createdAt, updatedAt, messageCount ->
            SessionRecord(
                id = id,
                profile = SessionRecord.SessionProfile(
                    title = title,
                    mode = SessionMode.fromDb(mode),
                ),
                createdAt = createdAt,
                updatedAt = updatedAt,
                stats = SessionRecord.SessionStats(
                    messageCount = messageCount,
                ),
                metadata = emptyMap(),
            )
        }.executeAsOneOrNull()

        session?.copy(metadata = getSessionMetadata(session.id))
    }

    suspend fun getSessionMetadataValue(sessionId: String, key: String): String? =
        getSessionById(sessionId)?.metadata?.get(key)

    suspend fun putSessionMetadataValue(
        sessionId: String,
        key: String,
        value: String,
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        val updatedMetadata = session.metadata.toMutableMap().apply {
            put(key, value)
        }
        upsertSession(
            session.copy(
                updatedAt = System.currentTimeMillis(),
                metadata = updatedMetadata,
            )
        )
        return true
    }

    suspend fun removeSessionMetadataValue(
        sessionId: String,
        key: String,
    ): Boolean {
        val session = getSessionById(sessionId) ?: return false
        if (!session.metadata.containsKey(key)) return true
        val updatedMetadata = session.metadata.toMutableMap().apply {
            remove(key)
        }
        upsertSession(
            session.copy(
                updatedAt = System.currentTimeMillis(),
                metadata = updatedMetadata,
            )
        )
        return true
    }

    suspend fun getStoryDraftCurrentByChapterId(chapterId: String): StoryDraftCurrentState? = query {
        queries.selectStoryDraftCurrentByChapterId(chapter_id = chapterId) { chapterIdValue, contentRef, checksum, wordCount, updatedAt, updatedBy ->
            StoryDraftCurrentState(
                chapterId = chapterIdValue,
                contentRef = contentRef,
                checksum = checksum,
                wordCount = wordCount,
                updatedAt = updatedAt,
                updatedBy = updatedBy,
            )
        }.executeAsOneOrNull()
    }

    suspend fun upsertStoryDraftCurrent(record: StoryDraftCurrentState) = query {
        queries.insertOrReplaceStoryDraftCurrent(
            chapter_id = record.chapterId,
            content_ref = record.contentRef,
            checksum = record.checksum,
            word_count = record.wordCount,
            updated_at = record.updatedAt,
            updated_by = record.updatedBy,
        )
    }

    suspend fun insertStoryDraftVersion(record: StoryDraftVersionState) = query {
        queries.insertStoryDraftVersion(
            id = record.id,
            chapter_id = record.chapterId,
            content_ref = record.contentRef,
            checksum = record.checksum,
            word_count = record.wordCount,
            created_at = record.createdAt,
            note = record.note,
            source = record.source,
        )
    }

    suspend fun getStoryDraftVersionsByChapterId(chapterId: String): List<StoryDraftVersionState> = query {
        queries.selectStoryDraftVersionsByChapterId(chapter_id = chapterId) { id, chapterIdValue, contentRef, checksum, wordCount, createdAt, note, source ->
            StoryDraftVersionState(
                id = id,
                chapterId = chapterIdValue,
                contentRef = contentRef,
                checksum = checksum,
                wordCount = wordCount,
                createdAt = createdAt,
                note = note,
                source = source,
            )
        }.executeAsList()
    }

    suspend fun getStoryDraftVersionById(
        chapterId: String,
        versionId: String,
    ): StoryDraftVersionState? = query {
        queries.selectStoryDraftVersionById(id = versionId, chapter_id = chapterId) { id, chapterIdValue, contentRef, checksum, wordCount, createdAt, note, source ->
            StoryDraftVersionState(
                id = id,
                chapterId = chapterIdValue,
                contentRef = contentRef,
                checksum = checksum,
                wordCount = wordCount,
                createdAt = createdAt,
                note = note,
                source = source,
            )
        }.executeAsOneOrNull()
    }

    suspend fun trimStoryDraftVersions(
        chapterId: String,
        keepLimit: Long,
    ) = query {
        queries.deleteStoryDraftVersionsOutsideLimit(
            chapter_id = chapterId,
            keep_limit = keepLimit,
        )
    }

    suspend fun insertStoryDraftProposal(record: StoryDraftProposalState) = query {
        queries.insertStoryDraftProposal(
            id = record.id,
            chapter_id = record.chapterId,
            base_checksum = record.baseChecksum,
            base_content_ref = record.baseContentRef,
            candidate_content_ref = record.candidateContentRef,
            candidate_checksum = record.candidateChecksum,
            operation_count = record.operationCount,
            status = record.status,
            created_at = record.createdAt,
            decided_at = record.decidedAt,
            note = record.note,
        )
    }

    suspend fun getStoryDraftProposalById(proposalId: String): StoryDraftProposalState? = query {
        queries.selectStoryDraftProposalById(id = proposalId) { id, chapterId, baseChecksum, baseContentRef, candidateContentRef, candidateChecksum, operationCount, status, createdAt, decidedAt, note ->
            StoryDraftProposalState(
                id = id,
                chapterId = chapterId,
                baseChecksum = baseChecksum,
                baseContentRef = baseContentRef,
                candidateContentRef = candidateContentRef,
                candidateChecksum = candidateChecksum,
                operationCount = operationCount,
                status = status,
                createdAt = createdAt,
                decidedAt = decidedAt,
                note = note,
            )
        }.executeAsOneOrNull()
    }

    suspend fun updateStoryDraftProposalStatus(
        proposalId: String,
        status: String,
        decidedAt: Long?,
    ) = query {
        queries.updateStoryDraftProposalStatus(
            id = proposalId,
            status = status,
            decided_at = decidedAt,
        )
    }

    suspend fun deleteStoryDraftProposalById(proposalId: String) = query {
        queries.deleteStoryDraftProposalById(id = proposalId)
    }

    suspend fun upsertStoryDraftPreviewState(record: StoryDraftPreviewState) = query {
        queries.insertOrReplaceStoryDraftPreviewState(
            chapter_id = record.chapterId,
            status = record.status,
            proposal_id = record.proposalId,
            before_content_ref = record.beforeContentRef,
            after_content_ref = record.afterContentRef,
            pending_ranges_json = record.pendingRangesJson,
            created_at = record.createdAt,
            updated_at = record.updatedAt,
        )
    }

    suspend fun getStoryDraftPreviewStateByChapterId(chapterId: String): StoryDraftPreviewState? = query {
        queries.selectStoryDraftPreviewStateByChapterId(chapter_id = chapterId) { chapterIdValue, status, proposalId, beforeContentRef, afterContentRef, pendingRangesJson, createdAt, updatedAt ->
            StoryDraftPreviewState(
                chapterId = chapterIdValue,
                status = status,
                proposalId = proposalId,
                beforeContentRef = beforeContentRef,
                afterContentRef = afterContentRef,
                pendingRangesJson = pendingRangesJson,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()
    }

    suspend fun getLatestStoryDraftPreviewByStoryId(storyId: String): StoryDraftPreviewState? = query {
        queries.selectLatestStoryDraftPreviewByStoryId(story_id = storyId) { chapterId, status, proposalId, beforeContentRef, afterContentRef, pendingRangesJson, createdAt, updatedAt ->
            StoryDraftPreviewState(
                chapterId = chapterId,
                status = status,
                proposalId = proposalId,
                beforeContentRef = beforeContentRef,
                afterContentRef = afterContentRef,
                pendingRangesJson = pendingRangesJson,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()
    }

    suspend fun upsertStoryChapterMemory(record: StoryChapterMemoryState) = query {
        queries.insertOrReplaceStoryChapterMemory(
            chapter_id = record.chapterId,
            story_id = record.storyId,
            approved_checksum = record.approvedChecksum,
            summary_short = record.summaryShort,
            summary_delta = record.summaryDelta,
            key_beats_json = record.keyBeatsJson,
            new_facts_json = record.newFactsJson,
            resolved_threads_json = record.resolvedThreadsJson,
            open_threads_json = record.openThreadsJson,
            continuity_risks_json = record.continuityRisksJson,
            warnings_json = record.warningsJson,
            entities_json = record.entitiesJson,
            summarizer_confidence = record.summarizerConfidence,
            summarizer_usable = if (record.summarizerUsable) 1 else 0,
            summarizer_model = record.summarizerModel,
            summarizer_run_id = record.summarizerRunId,
            pov = record.pov,
            tense = record.tense,
            updated_at = record.updatedAt,
        )
    }

    suspend fun getStoryChapterMemoryByChapterId(chapterId: String): StoryChapterMemoryState? = query {
        queries.selectStoryChapterMemoryByChapterId(chapter_id = chapterId) { chapterIdValue, storyId, approvedChecksum, summaryShort, summaryDelta, keyBeatsJson, newFactsJson, resolvedThreadsJson, openThreadsJson, continuityRisksJson, warningsJson, entitiesJson, summarizerConfidence, summarizerUsable, summarizerModel, summarizerRunId, pov, tense, updatedAt ->
            StoryChapterMemoryState(
                chapterId = chapterIdValue,
                storyId = storyId,
                approvedChecksum = approvedChecksum,
                summaryShort = summaryShort,
                summaryDelta = summaryDelta,
                keyBeatsJson = keyBeatsJson,
                newFactsJson = newFactsJson,
                resolvedThreadsJson = resolvedThreadsJson,
                openThreadsJson = openThreadsJson,
                continuityRisksJson = continuityRisksJson,
                warningsJson = warningsJson,
                entitiesJson = entitiesJson,
                summarizerConfidence = summarizerConfidence,
                summarizerUsable = summarizerUsable == 1L,
                summarizerModel = summarizerModel,
                summarizerRunId = summarizerRunId,
                pov = pov,
                tense = tense,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()
    }

    suspend fun listStoryChapterMemoryByStoryId(
        storyId: String,
        limit: Long,
    ): List<StoryChapterMemoryState> = query {
        queries.selectStoryChapterMemoryByStoryId(
            story_id = storyId,
            limit_value = limit,
        ) { chapterId, storyIdValue, approvedChecksum, summaryShort, summaryDelta, keyBeatsJson, newFactsJson, resolvedThreadsJson, openThreadsJson, continuityRisksJson, warningsJson, entitiesJson, summarizerConfidence, summarizerUsable, summarizerModel, summarizerRunId, pov, tense, updatedAt ->
            StoryChapterMemoryState(
                chapterId = chapterId,
                storyId = storyIdValue,
                approvedChecksum = approvedChecksum,
                summaryShort = summaryShort,
                summaryDelta = summaryDelta,
                keyBeatsJson = keyBeatsJson,
                newFactsJson = newFactsJson,
                resolvedThreadsJson = resolvedThreadsJson,
                openThreadsJson = openThreadsJson,
                continuityRisksJson = continuityRisksJson,
                warningsJson = warningsJson,
                entitiesJson = entitiesJson,
                summarizerConfidence = summarizerConfidence,
                summarizerUsable = summarizerUsable == 1L,
                summarizerModel = summarizerModel,
                summarizerRunId = summarizerRunId,
                pov = pov,
                tense = tense,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun replaceStoryChapterMemoryItems(
        chapterId: String,
        items: List<StoryChapterMemoryItemState>,
    ) = query {
        database.transaction {
            queries.deleteStoryChapterMemoryItemsByChapterId(chapter_id = chapterId)
            items.forEach { item ->
                queries.insertStoryChapterMemoryItem(
                    id = item.id,
                    chapter_id = item.chapterId,
                    story_id = item.storyId,
                    kind = item.kind,
                    value = item.value,
                    position = item.position,
                    source_chapter_id = item.sourceChapterId,
                    source_volume_id = item.sourceVolumeId,
                    status = item.status,
                    confidence = item.confidence,
                    created_at = item.createdAt,
                )
            }
        }
    }

    suspend fun listStoryChapterMemoryItemsByStoryId(
        storyId: String,
        limit: Long,
    ): List<StoryChapterMemoryItemState> = query {
        queries.selectStoryChapterMemoryItemsByStoryId(
            story_id = storyId,
            limit_value = limit,
        ) { id, chapterId, storyIdValue, kind, value, position, sourceChapterId, sourceVolumeId, status, confidence, createdAt ->
            StoryChapterMemoryItemState(
                id = id,
                chapterId = chapterId,
                storyId = storyIdValue,
                kind = kind,
                value = value,
                position = position,
                sourceChapterId = sourceChapterId,
                sourceVolumeId = sourceVolumeId,
                status = status,
                confidence = confidence,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun upsertStoryContinuityMemory(record: StoryContinuityMemoryState) = query {
        queries.insertOrReplaceStoryContinuityMemory(
            story_id = record.storyId,
            rolling_delta = record.rollingDelta,
            rolling_summary = record.rollingSummary,
            active_threads_json = record.activeThreadsJson,
            recent_new_facts_json = record.recentNewFactsJson,
            recent_resolved_threads_json = record.recentResolvedThreadsJson,
            continuity_warnings_json = record.continuityWarningsJson,
            last_chapter_ids_json = record.lastChapterIdsJson,
            packet_model = record.packetModel,
            packet_generated_at = record.packetGeneratedAt,
            updated_at = record.updatedAt,
        )
    }

    suspend fun getStoryContinuityMemoryByStoryId(storyId: String): StoryContinuityMemoryState? = query {
        queries.selectStoryContinuityMemoryByStoryId(story_id = storyId) { storyIdValue, rollingDelta, rollingSummary, activeThreadsJson, recentNewFactsJson, recentResolvedThreadsJson, continuityWarningsJson, lastChapterIdsJson, packetModel, packetGeneratedAt, updatedAt ->
            StoryContinuityMemoryState(
                storyId = storyIdValue,
                rollingDelta = rollingDelta,
                rollingSummary = rollingSummary,
                activeThreadsJson = activeThreadsJson,
                recentNewFactsJson = recentNewFactsJson,
                recentResolvedThreadsJson = recentResolvedThreadsJson,
                continuityWarningsJson = continuityWarningsJson,
                lastChapterIdsJson = lastChapterIdsJson,
                packetModel = packetModel,
                packetGeneratedAt = packetGeneratedAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()
    }

    suspend fun upsertStoryMemoryRetryQueue(record: StoryMemoryRetryQueueState) = query {
        queries.insertOrReplaceStoryMemoryRetryQueue(
            id = record.id,
            story_id = record.storyId,
            chapter_id = record.chapterId,
            approved_checksum = record.approvedChecksum,
            failure_reason = record.failureReason,
            attempt_count = record.attemptCount,
            next_attempt_at = record.nextAttemptAt,
            last_error = record.lastError,
            updated_at = record.updatedAt,
        )
    }

    suspend fun clearStoryMemoryRetryQueue(
        chapterId: String,
        approvedChecksum: String,
    ) = query {
        queries.deleteStoryMemoryRetryQueueByChapterAndChecksum(
            chapter_id = chapterId,
            approved_checksum = approvedChecksum,
        )
    }

    suspend fun deleteStoryDraftPreviewStateByStoryId(storyId: String) = query {
        queries.deleteStoryDraftPreviewStateByStoryId(story_id = storyId)
    }

    suspend fun deleteSession(sessionId: String) = query {
        queries.deleteSession(sessionId)
    }

    suspend fun deleteStoryById(storyId: String) = query {
        database.transaction {
            queries.deleteStoryStyleRefsByStoryId(storyId)
            queries.deleteStoryEmotionalBeatsByStoryId(storyId)
            queries.deleteStory(storyId)
        }
    }

    suspend fun upsertStory(record: StoryRecord) {
        query {
            database.transaction {
                val existing = queries.selectStoryById(record.id).executeAsOneOrNull()
                if (existing == null) {
                    queries.insertStory(
                        id = record.id,
                        session_id = record.sessionId,
                        title = record.title,
                        genre = record.genre,
                        setting = record.setting,
                        plot_outline = record.plotOutline,
                        logline = record.styleProfile?.logline,
                        theme = record.styleProfile?.theme,
                        tone = record.styleProfile?.tone,
                        stakes = record.styleProfile?.stakes,
                        pov = record.styleProfile?.pov,
                        tense = record.styleProfile?.tense,
                        target_audience = record.styleProfile?.targetAudience,
                        pacing = record.styleProfile?.pacing,
                        status = record.status?.name,
                        created_at = record.createdAt,
                        updated_at = record.updatedAt,
                    )
                } else {
                    queries.updateStory(
                        id = record.id,
                        title = record.title,
                        genre = record.genre,
                        setting = record.setting,
                        plot_outline = record.plotOutline,
                        logline = record.styleProfile?.logline,
                        theme = record.styleProfile?.theme,
                        tone = record.styleProfile?.tone,
                        stakes = record.styleProfile?.stakes,
                        pov = record.styleProfile?.pov,
                        tense = record.styleProfile?.tense,
                        target_audience = record.styleProfile?.targetAudience,
                        pacing = record.styleProfile?.pacing,
                        status = record.status?.name,
                        updated_at = record.updatedAt,
                    )
                }
                queries.deleteStoryStyleRefsByStoryId(record.id)
                record.styleRefs.forEachIndexed { index, value ->
                    queries.insertStoryStyleRef(
                        story_id = record.id,
                        position = index.toLong(),
                        value = value,
                    )
                }
                queries.deleteStoryEmotionalBeatsByStoryId(record.id)
                record.emotionalBeats.forEachIndexed { index, value ->
                    queries.insertStoryEmotionalBeat(
                        story_id = record.id,
                        position = index.toLong(),
                        value = value,
                    )
                }
            }
        }
        upsertEntityEmbedding(
            storyId = record.id,
            sourceRef = "story:${record.id}",
            text = EmbeddingTextBuilder.story(record),
        )
    }

    suspend fun getStoriesBySession(sessionId: String): List<StoryRecord> = query {
        val stories = queries.selectStoriesBySessionId(sessionId) { id, sessionId, title, genre, setting, plotOutline, logline, theme, tone, stakes, pov, tense, targetAudience, pacing, status, createdAt, updatedAt ->
            StoryRecord(
                id = id,
                sessionId = sessionId,
                title = title,
                genre = genre,
                setting = setting,
                plotOutline = plotOutline,
                status = status?.let(ContentStatus.Companion::fromDb),
                styleProfile = toStoryStyleProfile(
                    logline = logline,
                    theme = theme,
                    tone = tone,
                    stakes = stakes,
                    pov = pov,
                    tense = tense,
                    targetAudience = targetAudience,
                    pacing = pacing,
                ),
                styleRefs = emptyList(),
                emotionalBeats = emptyList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()

        stories.map { story ->
            story.copy(
                styleRefs = getStoryStyleRefs(story.id),
                emotionalBeats = getStoryEmotionalBeats(story.id),
            )
        }
    }

    suspend fun getStoryById(storyId: String): StoryRecord? = query {
        val story = queries.selectStoryById(storyId) { id, sessionId, title, genre, setting, plotOutline, logline, theme, tone, stakes, pov, tense, targetAudience, pacing, status, createdAt, updatedAt ->
            StoryRecord(
                id = id,
                sessionId = sessionId,
                title = title,
                genre = genre,
                setting = setting,
                plotOutline = plotOutline,
                status = status?.let(ContentStatus.Companion::fromDb),
                styleProfile = toStoryStyleProfile(
                    logline = logline,
                    theme = theme,
                    tone = tone,
                    stakes = stakes,
                    pov = pov,
                    tense = tense,
                    targetAudience = targetAudience,
                    pacing = pacing,
                ),
                styleRefs = emptyList(),
                emotionalBeats = emptyList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()

        story?.copy(
            styleRefs = getStoryStyleRefs(story.id),
            emotionalBeats = getStoryEmotionalBeats(story.id),
        )
    }


    suspend fun getChatContext(storyId: String): StoryChatContext {
        val story = getStoryById(storyId)
        val characters = getStoryCharacters(storyId)
        val locations = getLocationsByStory(storyId)
        val arcs = getArcsByStory(storyId)
        val worldRules = getWorldRulesByStory(storyId)
        val cultures = getCulturesByStory(storyId)
        val events = getEventsByStory(storyId)
        val organizations = getOrganizationsByStory(storyId)
        val relationships = getRelationshipsByStory(storyId)
        val locationFeatures = getLocationFeaturesByStory(storyId)
        val artifacts = getArtifactsByStory(storyId)
        val timelineEntries = getTimelineEntriesByStory(storyId)

        return StoryChatContext(
            story = story,
            characters = OverflowList(characters, 0),
            locations = OverflowList(locations, 0),
            arcs = OverflowList(arcs, 0),
            worldRules = OverflowList(worldRules, 0),
            cultures = OverflowList(cultures, 0),
            events = OverflowList(events, 0),
            organizations = OverflowList(organizations, 0),
            relationships = OverflowList(relationships, 0),
            locationFeatures = OverflowList(locationFeatures, 0),
            artifacts = OverflowList(artifacts, 0),
            timelineEntries = OverflowList(timelineEntries, 0),
        )
    }

    suspend fun getChatContextForAgent(storyId: String): StoryChatContext {
        val story = getStoryById(storyId)
        val characterSlice = limitLatest(getStoryCharacters(storyId), CHAT_CONTEXT_CHARACTER_LIMIT) { it.createdAt }
        val locationSlice = limitLatest(getLocationsByStory(storyId), CHAT_CONTEXT_LOCATION_LIMIT) { it.createdAt }
        val arcSlice = limitLatest(getArcsByStory(storyId), CHAT_CONTEXT_ARC_LIMIT) { it.updatedAt }
        val worldRuleSlice = limitLatest(getWorldRulesByStory(storyId), CHAT_CONTEXT_WORLD_RULE_LIMIT) { it.updatedAt }
        val cultureSlice = limitLatest(getCulturesByStory(storyId), CHAT_CONTEXT_CULTURE_LIMIT) { it.updatedAt }
        val eventSlice = limitLatest(getEventsByStory(storyId), CHAT_CONTEXT_EVENT_LIMIT) { it.updatedAt }
        val organizationSlice = limitLatest(getOrganizationsByStory(storyId), CHAT_CONTEXT_ORGANIZATION_LIMIT) { it.updatedAt }
        val relationshipSlice = limitLatest(getRelationshipsByStory(storyId), CHAT_CONTEXT_RELATIONSHIP_LIMIT) { it.updatedAt }
        val locationFeatureSlice = limitLatest(getLocationFeaturesByStory(storyId), CHAT_CONTEXT_LOCATION_FEATURE_LIMIT) { it.updatedAt }
        val artifactSlice = limitLatest(getArtifactsByStory(storyId), CHAT_CONTEXT_ARTIFACT_LIMIT) { it.updatedAt }
        val timelineSlice = limitLatest(getTimelineEntriesByStory(storyId), CHAT_CONTEXT_TIMELINE_LIMIT) { it.updatedAt }

        return StoryChatContext(
            story = story,
            characters = OverflowList(characterSlice.items, characterSlice.overflow),
            locations = OverflowList(locationSlice.items, locationSlice.overflow),
            arcs = OverflowList(arcSlice.items, arcSlice.overflow),
            worldRules = OverflowList(worldRuleSlice.items, worldRuleSlice.overflow),
            cultures = OverflowList(cultureSlice.items, cultureSlice.overflow),
            events = OverflowList(eventSlice.items, eventSlice.overflow),
            organizations = OverflowList(organizationSlice.items, organizationSlice.overflow),
            relationships = OverflowList(relationshipSlice.items, relationshipSlice.overflow),
            locationFeatures = OverflowList(locationFeatureSlice.items, locationFeatureSlice.overflow),
            artifacts = OverflowList(artifactSlice.items, artifactSlice.overflow),
            timelineEntries = OverflowList(timelineSlice.items, timelineSlice.overflow),
        )
    }

    suspend fun getStoryModeContext(storyId: String): StoryModeContext {
        val story = getStoryById(storyId)
        val volumes = getVolumesByStory(storyId)
        val chapters = volumes.flatMap { volume -> getChaptersByVolume(volume.id) }
        val scenes = chapters.flatMap { chapter -> getScenesByChapter(chapter.id) }
        val ragDocuments = chapters.flatMap { chapter -> getRagDocumentsByChapter(chapter.id) }

        return StoryModeContext(
            story = story,
            volumes = volumes,
            chapters = chapters,
            scenes = scenes,
            ragDocuments = ragDocuments,
        )
    }

    suspend fun replaceStoryCharacters(storyId: String, characters: List<StoryCharacterRecord>) {
        query {
            database.transaction {
                queries.deleteStoryCharacterTraitsByStoryId(storyId)
                queries.deleteStoryCharacterRolesByStoryId(storyId)
                queries.deleteStoryCharacterQuirksByStoryId(storyId)
                queries.deleteStoryCharactersByStoryId(storyId)
                characters.forEach { character ->
                    insertStoryCharacterInternal(storyId, character)
                    replaceStoryCharacterDetails(character)
                }
            }
        }
        characters.forEach { character ->
            upsertEntityEmbedding(
                storyId = storyId,
                sourceRef = "character:${character.id}",
                text = EmbeddingTextBuilder.character(character),
            )
        }
    }

    suspend fun insertStoryCharacter(record: StoryCharacterRecord) {
        query {
            database.transaction {
                insertStoryCharacterInternal(record.storyId, record)
                replaceStoryCharacterDetails(record)
            }
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "character:${record.id}",
            text = EmbeddingTextBuilder.character(record),
        )
    }

    suspend fun updateStoryCharacter(record: StoryCharacterRecord) {
        query {
            database.transaction {
                val physical = record.physical
                queries.updateStoryCharacter(
                    id = record.id,
                    story_id = record.storyId,
                    name = record.name,
                    description = record.description,
                    goal = record.goal,
                    motivation = record.motivation,
                    flaw = record.flaw,
                    temperament = record.temperament,
                    age = record.age,
                    pronouns = record.pronouns,
                    occupation = record.occupation,
                    backstory = record.backstory,
                    voice = record.voice,
                    internal_conflict = record.internalConflict,
                    appearance = physical?.appearance,
                    height = physical?.height,
                    build = physical?.build,
                    hair = physical?.hair,
                    eyes = physical?.eyes,
                    skin_tone = physical?.skinTone,
                    distinguishing_marks = physical?.distinguishingMarks,
                    style_notes = physical?.styleNotes,
                )
                replaceStoryCharacterDetails(record)
            }
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "character:${record.id}",
            text = EmbeddingTextBuilder.character(record),
        )
    }

    suspend fun deleteStoryCharacter(
        storyId: String,
        characterId: String,
    ) {
        query {
            queries.deleteStoryCharacterById(
                id = characterId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "character:$characterId")
    }

    suspend fun getStoryCharacters(storyId: String): List<StoryCharacterRecord> = query {
        val characters = queries.selectCharactersByStoryId(storyId) { id, storyId, name, description, goal, motivation, flaw, temperament, age, pronouns, occupation, backstory, voice, internalConflict, appearance, height, build, hair, eyes, skinTone, distinguishingMarks, styleNotes, createdAt ->
            StoryCharacterRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                traits = emptyList(),
                roles = emptyList(),
                goal = goal,
                motivation = motivation,
                flaw = flaw,
                temperament = temperament,
                age = age,
                pronouns = pronouns,
                occupation = occupation,
                backstory = backstory,
                voice = voice,
                internalConflict = internalConflict,
                quirks = emptyList(),
                physical = toPhysicalProfile(
                    appearance = appearance,
                    height = height,
                    build = build,
                    hair = hair,
                    eyes = eyes,
                    skinTone = skinTone,
                    distinguishingMarks = distinguishingMarks,
                    styleNotes = styleNotes,
                ),
                createdAt = createdAt,
            )
        }.executeAsList()

        characters.map { character ->
            character.copy(
                traits = getCharacterTraits(character.id),
                roles = getCharacterRoles(character.id),
                quirks = getCharacterQuirks(character.id),
            )
        }
    }

    private fun insertStoryCharacterInternal(storyId: String, character: StoryCharacterRecord) {
        val physical = character.physical
        queries.insertStoryCharacter(
            id = character.id,
            story_id = storyId,
            name = character.name,
            description = character.description,
            goal = character.goal,
            motivation = character.motivation,
            flaw = character.flaw,
            temperament = character.temperament,
            age = character.age,
            pronouns = character.pronouns,
            occupation = character.occupation,
            backstory = character.backstory,
            voice = character.voice,
            internal_conflict = character.internalConflict,
            appearance = physical?.appearance,
            height = physical?.height,
            build = physical?.build,
            hair = physical?.hair,
            eyes = physical?.eyes,
            skin_tone = physical?.skinTone,
            distinguishing_marks = physical?.distinguishingMarks,
            style_notes = physical?.styleNotes,
            created_at = character.createdAt,
        )
    }

    private fun replaceStoryCharacterDetails(character: StoryCharacterRecord) {
        queries.deleteStoryCharacterTraitsByCharacterId(character.id)
        queries.deleteStoryCharacterRolesByCharacterId(character.id)
        queries.deleteStoryCharacterQuirksByCharacterId(character.id)
        character.traits.forEachIndexed { index, value ->
            queries.insertStoryCharacterTrait(
                character_id = character.id,
                position = index.toLong(),
                value = value,
            )
        }
        character.roles.forEachIndexed { index, value ->
            queries.insertStoryCharacterRole(
                character_id = character.id,
                position = index.toLong(),
                value = value,
            )
        }
        character.quirks.forEachIndexed { index, value ->
            queries.insertStoryCharacterQuirk(
                character_id = character.id,
                position = index.toLong(),
                value = value,
            )
        }
    }

    fun observeStoryCharacters(storyId: String): Flow<List<StoryCharacterRecord>> =
        queries.selectCharactersByStoryId(storyId) { id, storyId, name, description, goal, motivation, flaw, temperament, age, pronouns, occupation, backstory, voice, internalConflict, appearance, height, build, hair, eyes, skinTone, distinguishingMarks, styleNotes, createdAt ->
            StoryCharacterRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                traits = emptyList(),
                roles = emptyList(),
                goal = goal,
                motivation = motivation,
                flaw = flaw,
                temperament = temperament,
                age = age,
                pronouns = pronouns,
                occupation = occupation,
                backstory = backstory,
                voice = voice,
                internalConflict = internalConflict,
                quirks = emptyList(),
                physical = toPhysicalProfile(
                    appearance = appearance,
                    height = height,
                    build = build,
                    hair = hair,
                    eyes = eyes,
                    skinTone = skinTone,
                    distinguishingMarks = distinguishingMarks,
                    styleNotes = styleNotes,
                ),
                createdAt = createdAt,
            )
        }
            .asFlow()
            .mapToList(coroutineDispatcher)
            .map { characters ->
                characters.map { character ->
                    character.copy(
                        traits = getCharacterTraits(character.id),
                        roles = getCharacterRoles(character.id),
                        quirks = getCharacterQuirks(character.id),
                    )
                }
            }
            .flowOn(coroutineDispatcher)

    suspend fun upsertVolume(record: StoryVolumeRecord) = query {
        database.transaction {
            val existing = queries.selectVolumeById(record.id).executeAsOneOrNull()
            if (existing == null) {
                val plan = record.plan
                queries.insertStoryVolume(
                    id = record.id,
                    story_id = record.storyId,
                    number = record.number,
                    title = record.title,
                    summary = plan?.summary,
                    target_word_count = plan?.targetWordCount,
                    status = plan?.status?.name,
                    notes = plan?.notes,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                )
            } else {
                val plan = record.plan
                queries.updateStoryVolume(
                    id = record.id,
                    title = record.title,
                    summary = plan?.summary,
                    target_word_count = plan?.targetWordCount,
                    status = plan?.status?.name,
                    notes = plan?.notes,
                    updated_at = record.updatedAt,
                )
            }
            queries.deleteStoryVolumeKeyEventsByVolumeId(record.id)
            record.keyEvents.forEachIndexed { index, value ->
                queries.insertStoryVolumeKeyEvent(
                    volume_id = record.id,
                    position = index.toLong(),
                    value = value,
                )
            }
        }
    }

    suspend fun getVolumesByStory(storyId: String): List<StoryVolumeRecord> = query {
        val volumes = queries.selectVolumesByStoryId(storyId) { id, storyId, number, title, summary, targetWordCount, status, notes, createdAt, updatedAt ->
            StoryVolumeRecord(
                id = id,
                storyId = storyId,
                number = number,
                title = title,
                plan = toVolumePlan(
                    summary = summary,
                    targetWordCount = targetWordCount,
                    status = status?.let(ContentStatus.Companion::fromDb),
                    notes = notes,
                ),
                keyEvents = emptyList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()

        volumes.map { volume ->
            volume.copy(keyEvents = getVolumeKeyEvents(volume.id))
        }
    }

    suspend fun getVolumeById(volumeId: String): StoryVolumeRecord? = query {
        val volume = queries.selectVolumeById(volumeId) { id, storyId, number, title, summary, targetWordCount, status, notes, createdAt, updatedAt ->
            StoryVolumeRecord(
                id = id,
                storyId = storyId,
                number = number,
                title = title,
                plan = toVolumePlan(
                    summary = summary,
                    targetWordCount = targetWordCount,
                    status = status?.let(ContentStatus.Companion::fromDb),
                    notes = notes,
                ),
                keyEvents = emptyList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()

        volume?.copy(keyEvents = getVolumeKeyEvents(volume.id))
    }

    suspend fun deleteStoryVolume(volumeId: String) = query {
        queries.deleteStoryVolume(volumeId)
    }

    fun observeVolumesByStory(storyId: String): Flow<List<StoryVolumeRecord>> =
        queries.selectVolumesByStoryId(storyId) { id, storyId, number, title, summary, targetWordCount, status, notes, createdAt, updatedAt ->
            StoryVolumeRecord(
                id = id,
                storyId = storyId,
                number = number,
                title = title,
                plan = toVolumePlan(
                    summary = summary,
                    targetWordCount = targetWordCount,
                    status = status?.let(ContentStatus.Companion::fromDb),
                    notes = notes,
                ),
                keyEvents = emptyList(),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
            .asFlow()
            .mapToList(coroutineDispatcher)
            .map { volumes ->
                volumes.map { volume ->
                    volume.copy(keyEvents = getVolumeKeyEvents(volume.id))
                }
            }
            .flowOn(coroutineDispatcher)

    suspend fun upsertChapter(record: StoryChapterRecord) = query {
        database.transaction {
            val existing = queries.selectChapterById(record.id).executeAsOneOrNull()
            if (existing == null) {
                val content = record.content
                queries.insertStoryChapter(
                    id = record.id,
                    volume_id = record.volumeId,
                    number = record.number,
                    title = record.title,
                    summary = record.summary,
                    content_ref = content?.ref,
                    content_type = content?.type?.name,
                    content_checksum = content?.checksum,
                    content_updated_at = content?.updatedAt,
                    content_range = content?.range,
                    word_count = content?.wordCount,
                    target_word_count = record.targetWordCount,
                    status = record.status?.name,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                )
            } else {
                val content = record.content
                queries.updateStoryChapter(
                    id = record.id,
                    title = record.title,
                    summary = record.summary,
                    content_ref = content?.ref,
                    content_type = content?.type?.name,
                    content_checksum = content?.checksum,
                    content_updated_at = content?.updatedAt,
                    content_range = content?.range,
                    word_count = content?.wordCount,
                    target_word_count = record.targetWordCount,
                    status = record.status?.name,
                    updated_at = record.updatedAt,
                )
            }
            queries.deleteStoryChapterKeyEventsByChapterId(record.id)
            record.keyEvents.forEachIndexed { index, value ->
                queries.insertStoryChapterKeyEvent(
                    chapter_id = record.id,
                    position = index.toLong(),
                    value = value,
                )
            }
        }
    }

    suspend fun getChaptersByVolume(volumeId: String): List<StoryChapterRecord> = query {
        val chapters = queries.selectChaptersByVolumeId(volumeId) { id, volumeId, number, title, summary, contentRef, contentType, contentChecksum, contentUpdatedAt, contentRange, wordCount, targetWordCount, status, createdAt, updatedAt ->
            StoryChapterRecord(
                id = id,
                volumeId = volumeId,
                number = number,
                title = title,
                summary = summary,
                content = toChapterContent(
                    contentRef = contentRef,
                    contentType = contentType?.let(ContentType.Companion::fromDb),
                    contentChecksum = contentChecksum,
                    contentUpdatedAt = contentUpdatedAt,
                    contentRange = contentRange,
                    wordCount = wordCount,
                ),
                keyEvents = emptyList(),
                targetWordCount = targetWordCount,
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()

        chapters.map { chapter ->
            chapter.copy(keyEvents = getChapterKeyEvents(chapter.id))
        }
    }

    suspend fun getChapterById(chapterId: String): StoryChapterRecord? = query {
        val chapter = queries.selectChapterById(chapterId) { id, volumeId, number, title, summary, contentRef, contentType, contentChecksum, contentUpdatedAt, contentRange, wordCount, targetWordCount, status, createdAt, updatedAt ->
            StoryChapterRecord(
                id = id,
                volumeId = volumeId,
                number = number,
                title = title,
                summary = summary,
                content = toChapterContent(
                    contentRef = contentRef,
                    contentType = contentType?.let(ContentType.Companion::fromDb),
                    contentChecksum = contentChecksum,
                    contentUpdatedAt = contentUpdatedAt,
                    contentRange = contentRange,
                    wordCount = wordCount,
                ),
                keyEvents = emptyList(),
                targetWordCount = targetWordCount,
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()

        chapter?.copy(keyEvents = getChapterKeyEvents(chapter.id))
    }

    suspend fun deleteStoryChapter(chapterId: String) = query {
        queries.deleteStoryChapter(chapterId)
    }

    fun observeChaptersByVolume(volumeId: String): Flow<List<StoryChapterRecord>> =
        queries.selectChaptersByVolumeId(volumeId) { id, volumeId, number, title, summary, contentRef, contentType, contentChecksum, contentUpdatedAt, contentRange, wordCount, targetWordCount, status, createdAt, updatedAt ->
            StoryChapterRecord(
                id = id,
                volumeId = volumeId,
                number = number,
                title = title,
                summary = summary,
                content = toChapterContent(
                    contentRef = contentRef,
                    contentType = contentType?.let(ContentType.Companion::fromDb),
                    contentChecksum = contentChecksum,
                    contentUpdatedAt = contentUpdatedAt,
                    contentRange = contentRange,
                    wordCount = wordCount,
                ),
                keyEvents = emptyList(),
                targetWordCount = targetWordCount,
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }
            .asFlow()
            .mapToList(coroutineDispatcher)
            .map { chapters ->
                chapters.map { chapter ->
                    chapter.copy(keyEvents = getChapterKeyEvents(chapter.id))
                }
            }
            .flowOn(coroutineDispatcher)

    suspend fun upsertLocation(record: StoryLocationRecord) {
        query {
            database.transaction {
                val existing = queries.selectLocationById(record.id).executeAsOneOrNull()
                if (existing == null) {
                    queries.insertStoryLocation(
                        id = record.id,
                        story_id = record.storyId,
                        name = record.profile.name,
                        description = record.profile.description,
                        created_at = record.createdAt,
                    )
                } else {
                    queries.updateStoryLocation(
                        id = record.id,
                        story_id = record.storyId,
                        name = record.profile.name,
                        description = record.profile.description,
                    )
                }
                queries.deleteStoryLocationTagsByLocationId(record.id)
                record.tags.forEachIndexed { index, value ->
                    queries.insertStoryLocationTag(
                        location_id = record.id,
                        position = index.toLong(),
                        value = value,
                    )
                }
            }
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "location:${record.id}",
            text = EmbeddingTextBuilder.location(record),
        )
    }

    suspend fun deleteStoryLocation(
        storyId: String,
        locationId: String,
    ) {
        query {
            queries.deleteStoryLocationById(
                id = locationId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "location:$locationId")
    }

    suspend fun getLocationsByStory(storyId: String): List<StoryLocationRecord> = query {
        val locations = queries.selectLocationsByStoryId(storyId) { id, storyId, name, description, createdAt ->
            StoryLocationRecord(
                id = id,
                storyId = storyId,
                profile = StoryLocationRecord.LocationProfile(
                    name = name,
                    description = description,
                ),
                tags = emptyList(),
                createdAt = createdAt,
            )
        }.executeAsList()

        locations.map { location ->
            location.copy(tags = getLocationTags(location.id))
        }
    }

    suspend fun replaceLocationsByStory(storyId: String, locations: List<StoryLocationRecord>) {
        query {
            database.transaction {
                queries.deleteStoryLocationTagsByStoryId(storyId)
                queries.deleteLocationsByStoryId(storyId)
                locations.forEach { location ->
                    queries.insertStoryLocation(
                        id = location.id,
                        story_id = storyId,
                        name = location.profile.name,
                        description = location.profile.description,
                        created_at = location.createdAt,
                    )
                    location.tags.forEachIndexed { index, value ->
                        queries.insertStoryLocationTag(
                            location_id = location.id,
                            position = index.toLong(),
                            value = value,
                        )
                    }
                }
            }
        }
        locations.forEach { location ->
            upsertEntityEmbedding(
                storyId = storyId,
                sourceRef = "location:${location.id}",
                text = EmbeddingTextBuilder.location(location),
            )
        }
    }

    suspend fun upsertArc(record: StoryArcRecord) {
        query {
            database.transaction {
                val existing = queries.selectArcById(record.id).executeAsOneOrNull()
                if (existing == null) {
                    queries.insertStoryArc(
                        id = record.id,
                        story_id = record.storyId,
                        scope_type = record.scopeType.name,
                        scope_id = record.scopeId,
                        title = record.title,
                        summary = record.summary,
                        status = record.status?.name,
                        created_at = record.createdAt,
                        updated_at = record.updatedAt,
                    )
                } else {
                    queries.updateStoryArc(
                        id = record.id,
                        story_id = record.storyId,
                        scope_type = record.scopeType.name,
                        scope_id = record.scopeId,
                        title = record.title,
                        summary = record.summary,
                        status = record.status?.name,
                        updated_at = record.updatedAt,
                    )
                }
            }
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "arc:${record.id}",
            text = EmbeddingTextBuilder.arc(record),
        )
    }

    suspend fun deleteStoryArc(
        storyId: String,
        arcId: String,
    ) {
        query {
            queries.deleteStoryArcById(
                id = arcId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "arc:$arcId")
    }

    suspend fun getArcsByStory(storyId: String): List<StoryArcRecord> = query {
        queries.selectArcsByStoryId(storyId) { id, storyId, scopeType, scopeId, title, summary, status, createdAt, updatedAt ->
            StoryArcRecord(
                id = id,
                storyId = storyId,
                scopeType = ArcScope.fromDb(scopeType),
                scopeId = scopeId,
                title = title,
                summary = summary,
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun getArcsByScope(scopeType: ArcScope, scopeId: String): List<StoryArcRecord> = query {
        queries.selectArcsByScope(scopeType.name, scopeId) { id, storyId, scopeType, scopeId, title, summary, status, createdAt, updatedAt ->
            StoryArcRecord(
                id = id,
                storyId = storyId,
                scopeType = ArcScope.fromDb(scopeType),
                scopeId = scopeId,
                title = title,
                summary = summary,
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun replaceArcsByStory(storyId: String, arcs: List<StoryArcRecord>) {
        query {
            database.transaction {
                queries.deleteArcsByStoryId(storyId)
                arcs.forEach { arc ->
                    queries.insertStoryArc(
                        id = arc.id,
                        story_id = storyId,
                        scope_type = arc.scopeType.name,
                        scope_id = arc.scopeId,
                        title = arc.title,
                        summary = arc.summary,
                        status = arc.status?.name,
                        created_at = arc.createdAt,
                        updated_at = arc.updatedAt,
                    )
                }
            }
        }
        arcs.forEach { arc ->
            upsertEntityEmbedding(
                storyId = storyId,
                sourceRef = "arc:${arc.id}",
                text = EmbeddingTextBuilder.arc(arc),
            )
        }
    }

    suspend fun replaceArcsByScope(scopeType: ArcScope, scopeId: String, arcs: List<StoryArcRecord>) {
        query {
            database.transaction {
                queries.deleteArcsByScope(scopeType.name, scopeId)
                arcs.forEach { arc ->
                    queries.insertStoryArc(
                        id = arc.id,
                        story_id = arc.storyId,
                        scope_type = arc.scopeType.name,
                        scope_id = arc.scopeId,
                        title = arc.title,
                        summary = arc.summary,
                        status = arc.status?.name,
                        created_at = arc.createdAt,
                        updated_at = arc.updatedAt,
                    )
                }
            }
        }
        arcs.forEach { arc ->
            upsertEntityEmbedding(
                storyId = arc.storyId,
                sourceRef = "arc:${arc.id}",
                text = EmbeddingTextBuilder.arc(arc),
            )
        }
    }

    suspend fun upsertScene(record: StorySceneRecord) = query {
        database.transaction {
            val existing = queries.selectSceneById(record.id).executeAsOneOrNull()
            if (existing == null) {
                val context = record.context
                queries.insertStoryScene(
                    id = record.id,
                    chapter_id = record.chapterId,
                    number = record.number,
                    title = record.title,
                    summary = record.summary,
                    content_range = context?.range,
                    pov = context?.pov,
                    emotional_beat = context?.emotionalBeat,
                    location_id = context?.locationId,
                    time_span = context?.timeSpan,
                    status = record.status?.name,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                )
            } else {
                val context = record.context
                queries.updateStoryScene(
                    id = record.id,
                    title = record.title,
                    summary = record.summary,
                    content_range = context?.range,
                    pov = context?.pov,
                    emotional_beat = context?.emotionalBeat,
                    location_id = context?.locationId,
                    time_span = context?.timeSpan,
                    status = record.status?.name,
                    updated_at = record.updatedAt,
                )
            }
            queries.deleteStorySceneKeyEventsBySceneId(record.id)
            record.keyEvents.forEachIndexed { index, value ->
                queries.insertStorySceneKeyEvent(
                    scene_id = record.id,
                    position = index.toLong(),
                    value = value,
                )
            }
        }
    }

    suspend fun getScenesByChapter(chapterId: String): List<StorySceneRecord> = query {
        val scenes = queries.selectScenesByChapterId(chapterId) { id, chapterId, number, title, summary, contentRange, pov, emotionalBeat, locationId, timeSpan, status, createdAt, updatedAt ->
            StorySceneRecord(
                id = id,
                chapterId = chapterId,
                number = number,
                title = title,
                summary = summary,
                context = toSceneContext(
                    contentRange = contentRange,
                    pov = pov,
                    emotionalBeat = emotionalBeat,
                    locationId = locationId,
                    timeSpan = timeSpan,
                ),
                keyEvents = emptyList(),
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()

        scenes.map { scene ->
            scene.copy(keyEvents = getSceneKeyEvents(scene.id))
        }
    }

    suspend fun getSceneById(sceneId: String): StorySceneRecord? = query {
        val scene = queries.selectSceneById(sceneId) { id, chapterId, number, title, summary, contentRange, pov, emotionalBeat, locationId, timeSpan, status, createdAt, updatedAt ->
            StorySceneRecord(
                id = id,
                chapterId = chapterId,
                number = number,
                title = title,
                summary = summary,
                context = toSceneContext(
                    contentRange = contentRange,
                    pov = pov,
                    emotionalBeat = emotionalBeat,
                    locationId = locationId,
                    timeSpan = timeSpan,
                ),
                keyEvents = emptyList(),
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsOneOrNull()

        scene?.copy(keyEvents = getSceneKeyEvents(scene.id))
    }

    suspend fun deleteStoryScene(sceneId: String) = query {
        queries.deleteStorySceneById(sceneId)
    }

    suspend fun replaceScenesByChapter(chapterId: String, scenes: List<StorySceneRecord>) = query {
        database.transaction {
            queries.deleteStorySceneKeyEventsByChapterId(chapterId)
            queries.deleteScenesByChapterId(chapterId)
            scenes.forEach { scene ->
                val context = scene.context
                queries.insertStoryScene(
                    id = scene.id,
                    chapter_id = chapterId,
                    number = scene.number,
                    title = scene.title,
                    summary = scene.summary,
                    content_range = context?.range,
                    pov = context?.pov,
                    emotional_beat = context?.emotionalBeat,
                    location_id = context?.locationId,
                    time_span = context?.timeSpan,
                    status = scene.status?.name,
                    created_at = scene.createdAt,
                    updated_at = scene.updatedAt,
                )
                scene.keyEvents.forEachIndexed { index, value ->
                    queries.insertStorySceneKeyEvent(
                        scene_id = scene.id,
                        position = index.toLong(),
                        value = value,
                    )
                }
            }
        }
    }

    suspend fun insertStoryWorldRule(record: StoryWorldRuleRecord) {
        query {
            queries.insertStoryWorldRule(
                id = record.id,
                story_id = record.storyId,
                title = record.title,
                description = record.description,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "world_rule:${record.id}",
            text = EmbeddingTextBuilder.worldRule(record),
        )
    }

    suspend fun updateStoryWorldRule(record: StoryWorldRuleRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryWorldRule(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                title = updatedRecord.title,
                description = updatedRecord.description,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "world_rule:${updatedRecord.id}",
            text = EmbeddingTextBuilder.worldRule(updatedRecord),
        )
    }

    suspend fun deleteStoryWorldRule(
        storyId: String,
        ruleId: String,
    ) {
        query {
            queries.deleteStoryWorldRuleById(
                id = ruleId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "world_rule:$ruleId")
    }

    suspend fun getWorldRulesByStory(storyId: String): List<StoryWorldRuleRecord> = query {
        queries.selectWorldRulesByStoryId(storyId) { id, storyId, title, description, createdAt, updatedAt ->
            StoryWorldRuleRecord(
                id = id,
                storyId = storyId,
                title = title,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryCulture(record: StoryCultureRecord) {
        query {
            queries.insertStoryCulture(
                id = record.id,
                story_id = record.storyId,
                name = record.name,
                description = record.description,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "culture:${record.id}",
            text = EmbeddingTextBuilder.culture(record),
        )
    }

    suspend fun updateStoryCulture(record: StoryCultureRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryCulture(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                name = updatedRecord.name,
                description = updatedRecord.description,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "culture:${updatedRecord.id}",
            text = EmbeddingTextBuilder.culture(updatedRecord),
        )
    }

    suspend fun deleteStoryCulture(
        storyId: String,
        cultureId: String,
    ) {
        query {
            queries.deleteStoryCultureById(
                id = cultureId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "culture:$cultureId")
    }

    suspend fun getCulturesByStory(storyId: String): List<StoryCultureRecord> = query {
        queries.selectCulturesByStoryId(storyId) { id, storyId, name, description, createdAt, updatedAt ->
            StoryCultureRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryEvent(record: StoryEventRecord) {
        query {
            queries.insertStoryEvent(
                id = record.id,
                story_id = record.storyId,
                name = record.name,
                description = record.description,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "event:${record.id}",
            text = EmbeddingTextBuilder.event(record),
        )
    }

    suspend fun updateStoryEvent(record: StoryEventRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryEvent(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                name = updatedRecord.name,
                description = updatedRecord.description,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "event:${updatedRecord.id}",
            text = EmbeddingTextBuilder.event(updatedRecord),
        )
    }

    suspend fun deleteStoryEvent(
        storyId: String,
        eventId: String,
    ) {
        query {
            queries.deleteStoryEventById(
                id = eventId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "event:$eventId")
    }

    suspend fun getEventsByStory(storyId: String): List<StoryEventRecord> = query {
        queries.selectEventsByStoryId(storyId) { id, storyId, name, description, createdAt, updatedAt ->
            StoryEventRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryOrganization(record: StoryOrganizationRecord) {
        query {
            queries.insertStoryOrganization(
                id = record.id,
                story_id = record.storyId,
                name = record.name,
                description = record.description,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "organization:${record.id}",
            text = EmbeddingTextBuilder.organization(record),
        )
    }

    suspend fun updateStoryOrganization(record: StoryOrganizationRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryOrganization(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                name = updatedRecord.name,
                description = updatedRecord.description,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "organization:${updatedRecord.id}",
            text = EmbeddingTextBuilder.organization(updatedRecord),
        )
    }

    suspend fun deleteStoryOrganization(
        storyId: String,
        organizationId: String,
    ) {
        query {
            queries.deleteStoryOrganizationById(
                id = organizationId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "organization:$organizationId")
    }

    suspend fun getOrganizationsByStory(storyId: String): List<StoryOrganizationRecord> = query {
        queries.selectOrganizationsByStoryId(storyId) { id, storyId, name, description, createdAt, updatedAt ->
            StoryOrganizationRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryRelationship(record: StoryRelationshipRecord) {
        val normalized = normalizeAndValidateRelationshipRecord(record)
        query {
            queries.insertStoryRelationship(
                id = normalized.id,
                story_id = normalized.storyId,
                subject_id = normalized.subjectId,
                subject_type = normalized.subjectType,
                object_id = normalized.objectId,
                object_type = normalized.objectType,
                relation = normalized.relation,
                notes = normalized.notes,
                created_at = normalized.createdAt,
                updated_at = normalized.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = normalized.storyId,
            sourceRef = "relationship:${normalized.id}",
            text = EmbeddingTextBuilder.relationship(normalized),
        )
    }

    suspend fun updateStoryRelationship(record: StoryRelationshipRecord) {
        val now = System.currentTimeMillis()
        val normalized = normalizeAndValidateRelationshipRecord(
            record = record.copy(updatedAt = now),
        )
        query {
            queries.updateStoryRelationship(
                id = normalized.id,
                story_id = normalized.storyId,
                subject_id = normalized.subjectId,
                subject_type = normalized.subjectType,
                object_id = normalized.objectId,
                object_type = normalized.objectType,
                relation = normalized.relation,
                notes = normalized.notes,
                updated_at = normalized.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = normalized.storyId,
            sourceRef = "relationship:${normalized.id}",
            text = EmbeddingTextBuilder.relationship(normalized),
        )
    }

    suspend fun deleteStoryRelationship(
        storyId: String,
        relationshipId: String,
    ) {
        query {
            queries.deleteStoryRelationshipById(
                id = relationshipId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "relationship:$relationshipId")
    }

    suspend fun getRelationshipsByStory(storyId: String): List<StoryRelationshipRecord> = query {
        queries.selectRelationshipsByStoryId(storyId) { id, storyId, subjectId, subjectType, objectId, objectType, relation, notes, createdAt, updatedAt ->
            StoryRelationshipRecord(
                id = id,
                storyId = storyId,
                subjectId = subjectId,
                subjectType = subjectType,
                objectId = objectId,
                objectType = objectType,
                relation = relation,
                notes = notes,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryLocationFeature(record: StoryLocationFeatureRecord) {
        query {
            queries.insertStoryLocationFeature(
                id = record.id,
                story_id = record.storyId,
                location_id = record.locationId,
                name = record.name,
                description = record.description,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "location_feature:${record.id}",
            text = EmbeddingTextBuilder.locationFeature(record),
        )
    }

    suspend fun updateStoryLocationFeature(record: StoryLocationFeatureRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryLocationFeature(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                location_id = updatedRecord.locationId,
                name = updatedRecord.name,
                description = updatedRecord.description,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "location_feature:${updatedRecord.id}",
            text = EmbeddingTextBuilder.locationFeature(updatedRecord),
        )
    }

    suspend fun deleteStoryLocationFeature(
        storyId: String,
        featureId: String,
    ) {
        query {
            queries.deleteStoryLocationFeatureById(
                id = featureId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "location_feature:$featureId")
    }

    suspend fun getLocationFeaturesByStory(storyId: String): List<StoryLocationFeatureRecord> = query {
        queries.selectLocationFeaturesByStoryId(storyId) { id, storyId, locationId, name, description, createdAt, updatedAt ->
            StoryLocationFeatureRecord(
                id = id,
                storyId = storyId,
                locationId = locationId,
                name = name,
                description = description,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryArtifact(record: StoryArtifactRecord) {
        query {
            queries.insertStoryArtifact(
                id = record.id,
                story_id = record.storyId,
                name = record.name,
                description = record.description,
                owner_id = record.ownerId,
                owner_type = record.ownerType,
                location_id = record.locationId,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }

        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "artifact:${record.id}",
            text = EmbeddingTextBuilder.artifact(record),
        )
    }

    suspend fun updateStoryArtifact(record: StoryArtifactRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryArtifact(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                name = updatedRecord.name,
                description = updatedRecord.description,
                owner_id = updatedRecord.ownerId,
                owner_type = updatedRecord.ownerType,
                location_id = updatedRecord.locationId,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "artifact:${updatedRecord.id}",
            text = EmbeddingTextBuilder.artifact(updatedRecord),
        )
    }

    suspend fun deleteStoryArtifact(
        storyId: String,
        artifactId: String,
    ) {
        query {
            queries.deleteStoryArtifactById(
                id = artifactId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "artifact:$artifactId")
    }

    suspend fun getArtifactsByStory(storyId: String): List<StoryArtifactRecord> = query {
        queries.selectArtifactsByStoryId(storyId) { id, storyId, name, description, ownerId, ownerType, locationId, createdAt, updatedAt ->
            StoryArtifactRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                ownerId = ownerId,
                ownerType = ownerType,
                locationId = locationId,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryTimelineEntry(record: StoryTimelineEntryRecord) {
        query {
            queries.insertStoryTimelineEntry(
                id = record.id,
                story_id = record.storyId,
                title = record.title,
                description = record.description,
                order_index = record.orderIndex,
                created_at = record.createdAt,
                updated_at = record.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = record.storyId,
            sourceRef = "timeline:${record.id}",
            text = EmbeddingTextBuilder.timelineEntry(record),
        )
    }

    suspend fun updateStoryTimelineEntry(record: StoryTimelineEntryRecord) {
        val now = System.currentTimeMillis()
        val updatedRecord = record.copy(updatedAt = now)
        query {
            queries.updateStoryTimelineEntry(
                id = updatedRecord.id,
                story_id = updatedRecord.storyId,
                title = updatedRecord.title,
                description = updatedRecord.description,
                order_index = updatedRecord.orderIndex,
                updated_at = updatedRecord.updatedAt,
            )
        }
        upsertEntityEmbedding(
            storyId = updatedRecord.storyId,
            sourceRef = "timeline:${updatedRecord.id}",
            text = EmbeddingTextBuilder.timelineEntry(updatedRecord),
        )
    }

    suspend fun deleteStoryTimelineEntry(
        storyId: String,
        entryId: String,
    ) {
        query {
            queries.deleteStoryTimelineEntryById(
                id = entryId,
                story_id = storyId,
            )
        }
        deleteEntityEmbedding(sourceRef = "timeline:$entryId")
    }

    suspend fun getTimelineEntriesByStory(storyId: String): List<StoryTimelineEntryRecord> = query {
        queries.selectTimelineEntriesByStoryId(storyId) { id, storyId, title, description, orderIndex, createdAt, updatedAt ->
            StoryTimelineEntryRecord(
                id = id,
                storyId = storyId,
                title = title,
                description = description,
                orderIndex = orderIndex,
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun insertRagDocument(record: RagDocumentRecord) = query {
        queries.insertRagDocument(
            doc_id = record.docId,
            session_id = record.sessionId,
            story_id = record.storyId,
            volume_id = record.volumeId,
            chapter_id = record.chapterId,
            chunk_index = record.chunkIndex,
            source_type = record.sourceType.name,
            source_ref = record.sourceRef,
            checksum = record.checksum,
            created_at = record.createdAt,
        )
    }

    suspend fun getRagDocumentsByChapter(chapterId: String): List<RagDocumentRecord> = query {
        queries.selectRagDocumentsByChapterId(chapterId) { docId, sessionId, storyId, volumeId, chapterId, chunkIndex, sourceType, sourceRef, checksum, createdAt ->
            RagDocumentRecord(
                docId = docId,
                sessionId = sessionId,
                storyId = storyId,
                volumeId = volumeId,
                chapterId = chapterId,
                chunkIndex = chunkIndex,
                sourceType = RagSourceType.fromDb(sourceType),
                sourceRef = sourceRef,
                checksum = checksum,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun deleteRagDocument(docId: String) = query {
        queries.deleteRagDocumentById(docId)
    }

    suspend fun reindexChatEmbeddings(storyId: String? = null) {
        val stories = if (storyId != null) {
            listOfNotNull(getStoryById(storyId))
        } else {
            getSessions().flatMap { session -> getStoriesBySession(session.id) }
        }

        stories.forEach { story ->
            upsertEntityEmbedding(
                storyId = story.id,
                sourceRef = "story:${story.id}",
                text = EmbeddingTextBuilder.story(story),
                mode = EmbeddingMode.CHAT,
            )

            getStoryCharacters(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "character:${record.id}",
                    text = EmbeddingTextBuilder.character(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getLocationsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "location:${record.id}",
                    text = EmbeddingTextBuilder.location(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getArcsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "arc:${record.id}",
                    text = EmbeddingTextBuilder.arc(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getWorldRulesByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "world_rule:${record.id}",
                    text = EmbeddingTextBuilder.worldRule(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getCulturesByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "culture:${record.id}",
                    text = EmbeddingTextBuilder.culture(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getEventsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "event:${record.id}",
                    text = EmbeddingTextBuilder.event(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getOrganizationsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "organization:${record.id}",
                    text = EmbeddingTextBuilder.organization(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getRelationshipsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "relationship:${record.id}",
                    text = EmbeddingTextBuilder.relationship(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getLocationFeaturesByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "location_feature:${record.id}",
                    text = EmbeddingTextBuilder.locationFeature(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getArtifactsByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "artifact:${record.id}",
                    text = EmbeddingTextBuilder.artifact(record),
                    mode = EmbeddingMode.CHAT,
                )
            }

            getTimelineEntriesByStory(story.id).forEach { record ->
                upsertEntityEmbedding(
                    storyId = story.id,
                    sourceRef = "timeline:${record.id}",
                    text = EmbeddingTextBuilder.timelineEntry(record),
                    mode = EmbeddingMode.CHAT,
                )
            }
        }
    }
}
