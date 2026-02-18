package com.ead.dispatch.sample.domain.agents.tools

import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.type.SessionMode
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftEditOperationRequest
import com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftEditType
import com.ead.dispatch.sample.domain.agents.tools.model.ProposeChapterDraftEditRequest
import com.ead.dispatch.sample.domain.agents.tools.model.QueryOutcome
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewPendingRange
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewSnapshot
import com.ead.dispatch.sample.domain.agents.tools.model.StoryDraftPreviewStatus
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class StoryDraftToolsPreviewIntegrationTest {

    @Test
    fun `story draft proposal persists latest preview snapshot and discard updates status`() = runBlocking {
        val tempDir = createTempDirectory("dispatch-story-preview-test")
        val databaseRuntime = DatabaseRuntime(
            factory = DispatchDatabaseFactory(
                appName = "dispatch-test",
                appAuthor = "ead-test",
                dbFileName = "story-preview-${UUID.randomUUID()}.db",
                dataDirectoryOverride = tempDir,
            )
        )

        try {
            val repository = StructuredIndexRepository(
                databaseRuntime = databaseRuntime,
                embeddingIndexService = EmbeddingIndexService(
                    embedderProvider = ChatAgentEmbedder(),
                    root = tempDir.resolve("embeddings"),
                ),
            )
            val tools = StoryDraftTools(repository)

            val now = Clock.System.now().toEpochMilliseconds()
            val sessionId = "session-${UUID.randomUUID()}"
            val storyId = "story-${UUID.randomUUID()}"
            val volumeId = "volume-${UUID.randomUUID()}"
            val chapterId = "chapter-${UUID.randomUUID()}"

            databaseRuntime.query<Unit> {
                databaseRuntime.queries.insertSession(
                    id = sessionId,
                    title = "Preview Test",
                    mode = SessionMode.CHAT_STORY.name,
                    created_at = now,
                    updated_at = now,
                    message_count = 0,
                )
                databaseRuntime.queries.insertStory(
                    id = storyId,
                    session_id = sessionId,
                    title = "Story Preview",
                    genre = null,
                    setting = null,
                    plot_outline = null,
                    logline = null,
                    theme = null,
                    tone = null,
                    stakes = null,
                    pov = null,
                    tense = null,
                    target_audience = null,
                    pacing = null,
                    status = null,
                    created_at = now,
                    updated_at = now,
                )
                databaseRuntime.queries.insertStoryVolume(
                    id = volumeId,
                    story_id = storyId,
                    number = 1,
                    title = "Volume 1",
                    summary = null,
                    target_word_count = null,
                    status = null,
                    notes = null,
                    created_at = now,
                    updated_at = now,
                )
                databaseRuntime.queries.insertStoryChapter(
                    id = chapterId,
                    volume_id = volumeId,
                    number = 1,
                    title = "Chapter 1",
                    summary = "Start",
                    content_ref = null,
                    content_type = null,
                    content_checksum = null,
                    content_updated_at = null,
                    content_range = null,
                    word_count = null,
                    target_word_count = null,
                    status = null,
                    created_at = now,
                    updated_at = now,
                )
            }

            val proposalResult = tools.proposeChapterDraftEdit(
                storyId = storyId,
                request = ProposeChapterDraftEditRequest(
                    chapterId = chapterId,
                    operations = listOf(
                        ChapterDraftEditOperationRequest(
                            type = ChapterDraftEditType.APPEND,
                            text = "\nSecond line.",
                        )
                    ),
                ),
            )
            val proposalSuccess = assertIs<ToolResult.Success<QueryOutcome<com.ead.dispatch.sample.domain.agents.tools.model.ChapterDraftProposalPayload>>>(proposalResult)
            val proposalPayload = proposalSuccess.data.payload

            assertEquals("Start", proposalPayload.baseText)
            assertEquals(true, proposalPayload.candidateText.contains("Second line."))

            val pendingSnapshot = loadSnapshot(repository, storyId)
            assertNotNull(pendingSnapshot)
            assertEquals(StoryDraftPreviewStatus.PENDING, pendingSnapshot.status)
            assertEquals(chapterId, pendingSnapshot.chapterId)
            assertEquals(proposalPayload.proposalId, pendingSnapshot.proposalId)

            val discardResult = tools.deleteChapterDraftProposal(
                storyId = storyId,
                proposalId = proposalPayload.proposalId,
            )
            assertIs<ToolResult.Success<*>>(discardResult)

            val rejectedSnapshot = loadSnapshot(repository, storyId)
            assertNotNull(rejectedSnapshot)
            assertEquals(StoryDraftPreviewStatus.REJECTED, rejectedSnapshot.status)
            assertEquals(proposalPayload.proposalId, rejectedSnapshot.proposalId)
        } finally {
            databaseRuntime.closeBlocking()
        }
    }

    private suspend fun loadSnapshot(
        repository: StructuredIndexRepository,
        storyId: String,
    ): StoryDraftPreviewSnapshot? {
        val latest = repository.getLatestStoryDraftPreviewByStoryId(storyId) ?: return null
        val chapter = repository.getChapterById(latest.chapterId) ?: return null
        return StoryDraftPreviewSnapshot(
            storyId = storyId,
            chapterId = latest.chapterId,
            chapterTitle = chapter.title,
            beforeText = latest.beforeText,
            afterText = latest.afterText,
            status = StoryDraftPreviewStatus.valueOf(latest.status),
            proposalId = latest.proposalId,
            pendingRanges = json.decodeFromString(
                ListSerializer(StoryDraftPreviewPendingRange.serializer()),
                latest.pendingRangesJson,
            ),
            createdAtEpochMillis = latest.createdAt,
            updatedAtEpochMillis = latest.updatedAt,
        )
    }

    private companion object {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
