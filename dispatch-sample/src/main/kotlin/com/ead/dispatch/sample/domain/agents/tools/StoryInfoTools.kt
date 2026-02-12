@file:Suppress("unused")

package com.ead.dispatch.sample.domain.agents.tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.tools.model.*
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

@Serializable
data class StoryModeContextSnapshot(
    val storyId: String,
    val title: String? = null,
    val status: String? = null,
    val volumes: List<StoryVolumeSnapshot> = emptyList(),
    val chapters: List<StoryChapterSnapshot> = emptyList(),
    val scenes: List<StorySceneSnapshot> = emptyList(),
    val ragDocuments: List<StoryRagDocumentSnapshot> = emptyList(),
)

@Serializable
data class StoryVolumeSnapshot(
    val id: String,
    val number: Long,
    val title: String,
    val status: String? = null,
    val summary: String? = null,
)

@Serializable
data class StoryChapterSnapshot(
    val id: String,
    val volumeId: String,
    val number: Long,
    val title: String,
    val status: String? = null,
    val summary: String? = null,
    val wordCount: Long? = null,
)

@Serializable
data class StorySceneSnapshot(
    val id: String,
    val chapterId: String,
    val number: Long,
    val title: String? = null,
    val status: String? = null,
    val summary: String? = null,
)

@Serializable
data class StoryRagDocumentSnapshot(
    val docId: String,
    val chapterId: String? = null,
    val sourceType: String,
    val sourceRef: String? = null,
    val createdAt: Long,
)

class StoryInfoTools(
    private val repository: StructuredIndexRepository,
) : ToolSet {

    @Tool
    @LLMDescription("Get the full chat context (story, characters, locations, arcs, world data).")
    suspend fun getChatContext(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<StoryChatContext>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val context = repository.getChatContext(storyId)
        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = context.story?.id,
            summary = "Loaded full chat context.",
            payload = context,
        )
    }

    @Tool
    @LLMDescription("Get story metadata for the current session.")
    suspend fun getStory(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<StoryRecord>> {
        val story = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = story.id,
            summary = "Loaded story metadata.",
            payload = story,
        )
    }

    @Tool
    @LLMDescription("Get story mode context (volumes, chapters, scenes, chapter rag refs).")
    suspend fun getStoryModeContext(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
    ): ToolResult<QueryOutcome<StoryModeContextSnapshot>> {
        repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session.")
        val context = repository.getStoryModeContext(storyId)
        val snapshot = StoryModeContextSnapshot(
            storyId = storyId,
            title = context.story?.title,
            status = context.story?.status?.name,
            volumes = context.volumes.map { volume ->
                StoryVolumeSnapshot(
                    id = volume.id,
                    number = volume.number,
                    title = volume.title,
                    status = volume.plan?.status?.name,
                    summary = volume.plan?.summary,
                )
            },
            chapters = context.chapters.map { chapter ->
                StoryChapterSnapshot(
                    id = chapter.id,
                    volumeId = chapter.volumeId,
                    number = chapter.number,
                    title = chapter.title,
                    status = chapter.status?.name,
                    summary = chapter.summary,
                    wordCount = chapter.content?.wordCount,
                )
            },
            scenes = context.scenes.map { scene ->
                StorySceneSnapshot(
                    id = scene.id,
                    chapterId = scene.chapterId,
                    number = scene.number,
                    title = scene.title,
                    status = scene.status?.name,
                    summary = scene.summary,
                )
            },
            ragDocuments = context.ragDocuments.map { document ->
                StoryRagDocumentSnapshot(
                    docId = document.docId,
                    chapterId = document.chapterId,
                    sourceType = document.sourceType.name,
                    sourceRef = document.sourceRef,
                    createdAt = document.createdAt,
                )
            },
        )
        return querySuccess(
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = context.story?.id,
            summary = "Loaded story mode context.",
            payload = snapshot,
        )
    }

    @Tool
    @LLMDescription("Initialize/Create a new story record.")
    suspend fun createStory(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Initial story metadata")
        request: StoryUpsertRequest,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getStoryById(storyId)
        if (existing != null) {
            return failure("ALREADY_EXISTS", "Story record already exists for this session. Use updateStory instead.")
        }

        val now = Clock.System.now().toEpochMilliseconds()
        // Create a new record based on the request
        val newStory = StoryRecord(
            id = storyId, // Using the session/story ID provided
            sessionId = storyId, // Assuming 1:1 mapping for now as per repo pattern
            title = request.title ?: "Untitled Story",
            genre = request.genre,
            setting = request.setting,
            plotOutline = request.plotOutline,
            status = request.status,
            styleProfile = mergeStyleProfile(null, request.styleProfile),
            styleRefs = request.styleRefs ?: emptyList(),
            emotionalBeats = request.emotionalBeats ?: emptyList(),
            createdAt = now,
            updatedAt = now
        )

        repository.upsertStory(newStory)

        return success(
            action = "create",
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = storyId,
            summary = "Created new story '${newStory.title}'.",
        )
    }

    @Tool
    @LLMDescription("Update existing story metadata (title, genre, setting, plot outline, style).")
    suspend fun updateStory(
        @LLMDescription("Story/session id for scoping")
        storyId: String,
        @LLMDescription("Story metadata to update")
        request: StoryUpsertRequest,
    ): ToolResult<OperationOutcome> {
        val existing = repository.getStoryById(storyId)
            ?: return failure("NOT_FOUND", "Story record not found for session. Use createStory first.")

        val updated = existing.copy(
            title = request.title ?: existing.title,
            genre = request.genre ?: existing.genre,
            setting = request.setting ?: existing.setting,
            plotOutline = request.plotOutline ?: existing.plotOutline,
            status = request.status ?: existing.status,
            styleProfile = mergeStyleProfile(existing.styleProfile, request.styleProfile),
            styleRefs = request.styleRefs ?: existing.styleRefs,
            emotionalBeats = request.emotionalBeats ?: existing.emotionalBeats,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )

        repository.upsertStory(updated)

        return success(
            action = "update",
            entity = OperationEntity.STORY,
            storyId = storyId,
            entityId = storyId,
            summary = "Updated story metadata.",
        )
    }

    // --- Helpers ---

    private fun mergeStyleProfile(
        current: StoryRecord.StoryStyleProfile?,
        request: StoryStyleProfileRequest?,
    ): StoryRecord.StoryStyleProfile? {
        val base = current ?: StoryRecord.StoryStyleProfile()
        val merged = request?.let {
            base.copy(
                logline = it.logline ?: base.logline,
                theme = it.theme ?: base.theme,
                tone = it.tone ?: base.tone,
                stakes = it.stakes ?: base.stakes,
                pov = it.pov ?: base.pov,
                tense = it.tense ?: base.tense,
                targetAudience = it.targetAudience ?: base.targetAudience,
                pacing = it.pacing ?: base.pacing,
            )
        } ?: base
        return if (merged == StoryRecord.StoryStyleProfile()) null else merged
    }
}
