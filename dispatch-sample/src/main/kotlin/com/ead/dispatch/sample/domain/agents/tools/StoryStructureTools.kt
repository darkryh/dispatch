@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterContentRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateChapterRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateSceneRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateVolumeRequest
import com.ead.dispatch.sample.domain.agents.tools.model.OperationEntity
import com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.SceneContextRequest
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateChapterRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateSceneRequest
import com.ead.dispatch.sample.domain.agents.tools.model.UpdateVolumeRequest
import kotlinx.datetime.Clock
import java.util.UUID

class StoryStructureTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

    @Tool
    @LLMDescription("List all volumes for a story.")
    suspend fun listVolumes(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<List<StoryVolumeRecord>>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val volumes = repository.getVolumesByStory(storyId)
        return querySuccess(
            entity = OperationEntity.VOLUME,
            storyId = storyId,
            entityId = storyId,
            summary = "Loaded ${volumes.size} volume(s).",
            payload = volumes,
        )
    }

    @Tool
    @LLMDescription("Get a single volume by id.")
    suspend fun getVolume(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Volume id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryVolumeRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val volume = repository.getVolumeById(entityId)
            ?.takeIf { it.storyId == storyId }
            ?: return failure("NOT_FOUND", "Volume with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.VOLUME,
            storyId = storyId,
            entityId = volume.id,
            summary = "Loaded volume '${volume.title}'.",
            payload = volume,
        )
    }

    @Tool
    @LLMDescription("Create a volume in the story.")
    suspend fun createVolume(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Volume fields to create")
        request: CreateVolumeRequest,
    ): ToolResult<OperationOutcome> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")

        val title = request.title.trim()
        if (title.isBlank()) return failure("MISSING_FIELD", "Volume title is required.")
        if (request.number < 1) return failure("INVALID_FIELD", "Volume number must be >= 1.")
        if (request.targetWordCount != null && request.targetWordCount < 0) {
            return failure("INVALID_FIELD", "Volume targetWordCount must be >= 0.")
        }

        val duplicate = repository.getVolumesByStory(storyId).any { it.number == request.number }
        if (duplicate) {
            return failure("CONFLICT", "Volume number ${request.number} already exists.")
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val record = StoryVolumeRecord(
            id = UUID.randomUUID().toString(),
            storyId = storyId,
            number = request.number,
            title = title,
            plan = toVolumePlan(
                summary = request.summary,
                targetWordCount = request.targetWordCount,
                status = request.status,
                notes = request.notes,
            ),
            keyEvents = normalizeValues(request.keyEvents),
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertVolume(record)
        return success(
            action = "create",
            entity = OperationEntity.VOLUME,
            storyId = storyId,
            entityId = record.id,
            summary = "Created volume ${record.number}: '${record.title}'.",
        )
    }

    @Tool
    @LLMDescription("Update volume metadata.")
    suspend fun updateVolume(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Volume id to update")
        entityId: String,
        @LLMDescription("Volume fields to update")
        request: UpdateVolumeRequest = UpdateVolumeRequest(),
    ): ToolResult<OperationOutcome> {
        val existing = repository.getVolumeById(entityId)
            ?.takeIf { it.storyId == storyId }
            ?: return failure("NOT_FOUND", "Volume with id '$entityId' not found.")

        val title = request.title?.trim()
        if (request.title != null && title.isNullOrBlank()) {
            return failure("INVALID_FIELD", "Volume title cannot be blank.")
        }
        if (request.targetWordCount != null && request.targetWordCount < 0) {
            return failure("INVALID_FIELD", "Volume targetWordCount must be >= 0.")
        }

        val updated = existing.copy(
            title = title ?: existing.title,
            plan = toVolumePlan(
                summary = updateNullableText(existing.plan?.summary, request.summary),
                targetWordCount = request.targetWordCount ?: existing.plan?.targetWordCount,
                status = request.status ?: existing.plan?.status,
                notes = updateNullableText(existing.plan?.notes, request.notes),
            ),
            keyEvents = request.keyEvents?.let(::normalizeValues) ?: existing.keyEvents,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.upsertVolume(updated)
        return success(
            action = "update",
            entity = OperationEntity.VOLUME,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated volume ${updated.number}: '${updated.title}'.",
        )
    }

    @Tool
    @LLMDescription("Delete a volume. Use force=true when the volume has child chapters/scenes.")
    suspend fun deleteVolume(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Volume id to delete")
        entityId: String,
        @LLMDescription("Confirm cascade delete when this volume has chapters/scenes")
        force: Boolean = false,
    ): ToolResult<OperationOutcome> {
        val volume = repository.getVolumeById(entityId)
            ?.takeIf { it.storyId == storyId }
            ?: return failure("NOT_FOUND", "Volume with id '$entityId' not found.")

        val chapters = repository.getChaptersByVolume(volume.id)
        val scenes = chapters.sumOf { chapter -> repository.getScenesByChapter(chapter.id).size }
        if (!force && (chapters.isNotEmpty() || scenes > 0)) {
            return failure(
                "CONFLICT",
                "Volume has ${chapters.size} chapter(s) and $scenes scene(s). Retry with force=true to cascade delete.",
            )
        }

        repository.deleteStoryVolume(volume.id)
        return success(
            action = "delete",
            entity = OperationEntity.VOLUME,
            storyId = storyId,
            entityId = volume.id,
            summary = "Deleted volume ${volume.number} ('${volume.title}') and cascaded ${chapters.size} chapter(s), $scenes scene(s).",
        )
    }

    @Tool
    @LLMDescription("List chapters in a story. Optionally filter by volume id.")
    suspend fun listChapters(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Optional volume id filter")
        volumeId: String? = null,
    ): ToolResult<QueryOutcome<List<StoryChapterRecord>>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val chapters = if (volumeId.isNullOrBlank()) {
            repository.getVolumesByStory(storyId)
                .flatMap { volume -> repository.getChaptersByVolume(volume.id) }
                .sortedWith(compareBy<StoryChapterRecord> { it.volumeId }.thenBy { it.number })
        } else {
            val volume = repository.getVolumeById(volumeId)
                ?.takeIf { it.storyId == storyId }
                ?: return failure("NOT_FOUND", "Volume with id '$volumeId' not found.")
            repository.getChaptersByVolume(volume.id).sortedBy { it.number }
        }
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = storyId,
            summary = "Loaded ${chapters.size} chapter(s).",
            payload = chapters,
        )
    }

    @Tool
    @LLMDescription("Get a chapter by id.")
    suspend fun getChapter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StoryChapterRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val chapter = chapterInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Chapter with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Loaded chapter '${chapter.title}'.",
            payload = chapter,
        )
    }

    @Tool
    @LLMDescription("Create a chapter in a volume.")
    suspend fun createChapter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter fields to create")
        request: CreateChapterRequest,
    ): ToolResult<OperationOutcome> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")

        val volume = repository.getVolumeById(request.volumeId)
            ?.takeIf { it.storyId == storyId }
            ?: return failure("NOT_FOUND", "Volume with id '${request.volumeId}' not found.")
        val title = request.title.trim()
        if (title.isBlank()) return failure("MISSING_FIELD", "Chapter title is required.")
        if (request.number < 1) return failure("INVALID_FIELD", "Chapter number must be >= 1.")
        if (request.targetWordCount != null && request.targetWordCount < 0) {
            return failure("INVALID_FIELD", "Chapter targetWordCount must be >= 0.")
        }

        val duplicate = repository.getChaptersByVolume(volume.id).any { it.number == request.number }
        if (duplicate) return failure("CONFLICT", "Chapter number ${request.number} already exists in this volume.")

        val content = toChapterContent(request.content)
            ?: run {
                if (request.content != null) return failure("INVALID_FIELD", "Chapter content metadata is invalid.")
                null
            }

        val now = Clock.System.now().toEpochMilliseconds()
        val record = StoryChapterRecord(
            id = UUID.randomUUID().toString(),
            volumeId = volume.id,
            number = request.number,
            title = title,
            summary = normalizeOptionalText(request.summary),
            content = content,
            keyEvents = normalizeValues(request.keyEvents),
            targetWordCount = request.targetWordCount,
            status = request.status,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertChapter(record)
        return success(
            action = "create",
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = record.id,
            summary = "Created chapter ${record.number}: '${record.title}'.",
        )
    }

    @Tool
    @LLMDescription("Update chapter metadata and content pointers.")
    suspend fun updateChapter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id to update")
        entityId: String,
        @LLMDescription("Chapter fields to update")
        request: UpdateChapterRequest = UpdateChapterRequest(),
    ): ToolResult<OperationOutcome> {
        val existing = chapterInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Chapter with id '$entityId' not found.")
        val title = request.title?.trim()
        if (request.title != null && title.isNullOrBlank()) {
            return failure("INVALID_FIELD", "Chapter title cannot be blank.")
        }
        if (request.targetWordCount != null && request.targetWordCount < 0) {
            return failure("INVALID_FIELD", "Chapter targetWordCount must be >= 0.")
        }

        val content = when {
            request.content == null -> existing.content
            else -> toChapterContent(request.content)
                ?: return failure("INVALID_FIELD", "Chapter content metadata is invalid.")
        }

        val updated = existing.copy(
            title = title ?: existing.title,
            summary = updateNullableText(existing.summary, request.summary),
            content = content,
            keyEvents = request.keyEvents?.let(::normalizeValues) ?: existing.keyEvents,
            targetWordCount = request.targetWordCount ?: existing.targetWordCount,
            status = request.status ?: existing.status,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.upsertChapter(updated)
        return success(
            action = "update",
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated chapter ${updated.number}: '${updated.title}'.",
        )
    }

    @Tool
    @LLMDescription("Delete a chapter. Use force=true when the chapter has child scenes.")
    suspend fun deleteChapter(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Chapter id to delete")
        entityId: String,
        @LLMDescription("Confirm cascade delete when this chapter has scenes")
        force: Boolean = false,
    ): ToolResult<OperationOutcome> {
        val chapter = chapterInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Chapter with id '$entityId' not found.")
        val scenes = repository.getScenesByChapter(chapter.id)
        if (!force && scenes.isNotEmpty()) {
            return failure(
                "CONFLICT",
                "Chapter has ${scenes.size} scene(s). Retry with force=true to cascade delete.",
            )
        }

        repository.deleteStoryChapter(chapter.id)
        return success(
            action = "delete",
            entity = OperationEntity.CHAPTER,
            storyId = storyId,
            entityId = chapter.id,
            summary = "Deleted chapter ${chapter.number} ('${chapter.title}') and cascaded ${scenes.size} scene(s).",
        )
    }

    @Tool
    @LLMDescription("List scenes in a story. Optionally filter by chapter id.")
    suspend fun listScenes(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Optional chapter id filter")
        chapterId: String? = null,
    ): ToolResult<QueryOutcome<List<StorySceneRecord>>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val scenes = if (chapterId.isNullOrBlank()) {
            repository.getVolumesByStory(storyId)
                .flatMap { volume -> repository.getChaptersByVolume(volume.id) }
                .flatMap { chapter -> repository.getScenesByChapter(chapter.id) }
        } else {
            val chapter = chapterInStory(storyId, chapterId)
                ?: return failure("NOT_FOUND", "Chapter with id '$chapterId' not found.")
            repository.getScenesByChapter(chapter.id).sortedBy { it.number }
        }
        return querySuccess(
            entity = OperationEntity.SCENE,
            storyId = storyId,
            entityId = storyId,
            summary = "Loaded ${scenes.size} scene(s).",
            payload = scenes,
        )
    }

    @Tool
    @LLMDescription("Get a scene by id.")
    suspend fun getScene(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Scene id to fetch")
        entityId: String,
    ): ToolResult<QueryOutcome<StorySceneRecord>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val scene = sceneInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Scene with id '$entityId' not found.")
        return querySuccess(
            entity = OperationEntity.SCENE,
            storyId = storyId,
            entityId = scene.id,
            summary = "Loaded scene '${scene.title ?: "(untitled scene)"}'.",
            payload = scene,
        )
    }

    @Tool
    @LLMDescription("Create a scene in a chapter.")
    suspend fun createScene(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Scene fields to create")
        request: CreateSceneRequest,
    ): ToolResult<OperationOutcome> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val chapter = chapterInStory(storyId, request.chapterId)
            ?: return failure("NOT_FOUND", "Chapter with id '${request.chapterId}' not found.")
        if (request.number < 1) return failure("INVALID_FIELD", "Scene number must be >= 1.")

        val duplicate = repository.getScenesByChapter(chapter.id).any { it.number == request.number }
        if (duplicate) return failure("CONFLICT", "Scene number ${request.number} already exists in this chapter.")

        val context = toSceneContext(request.context)
            ?: run {
                if (request.context != null) return failure("INVALID_FIELD", "Scene context is invalid.")
                null
            }
        val locationId = context?.locationId
        if (!locationId.isNullOrBlank()) {
            val locationExists = repository.getLocationsByStory(storyId).any { it.id == locationId }
            if (!locationExists) {
                return failure("NOT_FOUND", "Scene locationId '$locationId' does not exist in this story.")
            }
        }

        val now = Clock.System.now().toEpochMilliseconds()
        val record = StorySceneRecord(
            id = UUID.randomUUID().toString(),
            chapterId = chapter.id,
            number = request.number,
            title = normalizeOptionalText(request.title),
            summary = normalizeOptionalText(request.summary),
            context = context,
            keyEvents = normalizeValues(request.keyEvents),
            status = request.status,
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertScene(record)
        return success(
            action = "create",
            entity = OperationEntity.SCENE,
            storyId = storyId,
            entityId = record.id,
            summary = "Created scene ${record.number} in chapter '${chapter.title}'.",
        )
    }

    @Tool
    @LLMDescription("Update scene metadata and context.")
    suspend fun updateScene(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Scene id to update")
        entityId: String,
        @LLMDescription("Scene fields to update")
        request: UpdateSceneRequest = UpdateSceneRequest(),
    ): ToolResult<OperationOutcome> {
        val existing = sceneInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Scene with id '$entityId' not found.")

        val context = when {
            request.context == null -> existing.context
            else -> toSceneContext(request.context)
                ?: return failure("INVALID_FIELD", "Scene context is invalid.")
        }
        val locationId = context?.locationId
        if (!locationId.isNullOrBlank()) {
            val locationExists = repository.getLocationsByStory(storyId).any { it.id == locationId }
            if (!locationExists) {
                return failure("NOT_FOUND", "Scene locationId '$locationId' does not exist in this story.")
            }
        }

        val updated = existing.copy(
            title = updateNullableText(existing.title, request.title),
            summary = updateNullableText(existing.summary, request.summary),
            context = context,
            keyEvents = request.keyEvents?.let(::normalizeValues) ?: existing.keyEvents,
            status = request.status ?: existing.status,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        repository.upsertScene(updated)
        return success(
            action = "update",
            entity = OperationEntity.SCENE,
            storyId = storyId,
            entityId = updated.id,
            summary = "Updated scene ${updated.number}.",
        )
    }

    @Tool
    @LLMDescription("Delete a scene.")
    suspend fun deleteScene(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Scene id to delete")
        entityId: String,
    ): ToolResult<OperationOutcome> {
        val scene = sceneInStory(storyId, entityId)
            ?: return failure("NOT_FOUND", "Scene with id '$entityId' not found.")
        repository.deleteStoryScene(scene.id)
        return success(
            action = "delete",
            entity = OperationEntity.SCENE,
            storyId = storyId,
            entityId = scene.id,
            summary = "Deleted scene ${scene.number}.",
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

    private suspend fun sceneInStory(
        storyId: String,
        sceneId: String,
    ): StorySceneRecord? {
        val scene = repository.getSceneById(sceneId) ?: return null
        val chapter = chapterInStory(storyId, scene.chapterId) ?: return null
        return if (chapter.id == scene.chapterId) scene else null
    }

    private fun toVolumePlan(
        summary: String?,
        targetWordCount: Long?,
        status: com.ead.dispatch.sample.data.db.type.ContentStatus?,
        notes: String?,
    ): StoryVolumeRecord.VolumePlan? {
        val normalizedSummary = normalizeOptionalText(summary)
        val normalizedNotes = normalizeOptionalText(notes)
        if (normalizedSummary == null && targetWordCount == null && status == null && normalizedNotes == null) {
            return null
        }
        return StoryVolumeRecord.VolumePlan(
            summary = normalizedSummary,
            targetWordCount = targetWordCount,
            status = status,
            notes = normalizedNotes,
        )
    }

    private fun toChapterContent(
        request: ChapterContentRequest?,
    ): StoryChapterRecord.ChapterContent? {
        if (request == null) return null
        val ref = normalizeOptionalText(request.ref)
        val range = normalizeOptionalText(request.range)
        val checksum = normalizeOptionalText(request.checksum)
        if (request.wordCount != null && request.wordCount < 0) return null
        if (range != null && ref == null) return null
        if (checksum != null && ref == null) return null
        if (request.updatedAt != null && ref == null) return null
        if (ref == null && request.type != null) return null

        val hasData = ref != null ||
            request.type != null ||
            checksum != null ||
            request.updatedAt != null ||
            range != null ||
            request.wordCount != null
        if (!hasData) return null

        return StoryChapterRecord.ChapterContent(
            ref = ref,
            type = request.type,
            checksum = checksum,
            updatedAt = request.updatedAt,
            range = range,
            wordCount = request.wordCount,
        )
    }

    private fun toSceneContext(
        request: SceneContextRequest?,
    ): StorySceneRecord.SceneContext? {
        if (request == null) return null
        val range = normalizeOptionalText(request.range)
        val pov = normalizeOptionalText(request.pov)
        val emotionalBeat = normalizeOptionalText(request.emotionalBeat)
        val locationId = normalizeOptionalText(request.locationId)
        val timeSpan = normalizeOptionalText(request.timeSpan)

        if (range == null && pov == null && emotionalBeat == null && locationId == null && timeSpan == null) {
            return null
        }
        return StorySceneRecord.SceneContext(
            range = range,
            pov = pov,
            emotionalBeat = emotionalBeat,
            locationId = locationId,
            timeSpan = timeSpan,
        )
    }

    private fun updateNullableText(
        current: String?,
        incoming: String?,
    ): String? = when (incoming) {
        null -> current
        else -> normalizeOptionalText(incoming)
    }

    private fun normalizeOptionalText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() }

    private fun normalizeValues(values: List<String>?): List<String> =
        values.orEmpty()
            .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() } }
            .distinct()
}
