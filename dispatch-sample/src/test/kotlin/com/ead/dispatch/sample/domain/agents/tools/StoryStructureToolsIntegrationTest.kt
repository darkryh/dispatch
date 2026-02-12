package com.ead.dispatch.sample.domain.agents.tools

import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.type.SessionMode
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.agents.tools.model.CreateChapterRequest
import com.ead.dispatch.sample.domain.agents.tools.model.CreateVolumeRequest
import com.ead.dispatch.sample.domain.agents.tools.model.ToolResult
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class StoryStructureToolsIntegrationTest {

    @Test
    fun `create chapter persists correctly in story mode`() = runBlocking {
        val tempDir = createTempDirectory("dispatch-story-tools-test")
        val databaseRuntime = DatabaseRuntime(
            factory = DispatchDatabaseFactory(
                appName = "dispatch-test",
                appAuthor = "ead-test",
                dbFileName = "story-tools-${UUID.randomUUID()}.db",
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
            val tools = StoryStructureTools(repository)

            val now = Clock.System.now().toEpochMilliseconds()
            val sessionId = "session-${UUID.randomUUID()}"
            val storyId = "story-${UUID.randomUUID()}"

            databaseRuntime.query {
                databaseRuntime.queries.insertSession(
                    id = sessionId,
                    title = "Threading Regression Test",
                    mode = SessionMode.CHAT_STORY.name,
                    created_at = now,
                    updated_at = now,
                    message_count = 0,
                )
                databaseRuntime.queries.insertStory(
                    id = storyId,
                    session_id = sessionId,
                    title = "Test Story",
                    genre = "Fantasy",
                    setting = "City",
                    plot_outline = "Outline",
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
            }

            val volumeResult = tools.createVolume(
                storyId = storyId,
                request = CreateVolumeRequest(
                    number = 1,
                    title = "Volume 1",
                    summary = "Summary",
                ),
            )
            val volumeSuccess = assertIs<ToolResult.Success<*>>(volumeResult)
            val volumeOutcome = volumeSuccess.data as com.ead.dispatch.sample.domain.agents.tools.model.OperationOutcome
            val volumeId = assertNotNull(volumeOutcome.entityId)

            val chapterResult = tools.createChapter(
                storyId = storyId,
                request = CreateChapterRequest(
                    volumeId = volumeId,
                    number = 1,
                    title = "Chapter 1",
                    summary = "Opening chapter",
                ),
            )
            assertIs<ToolResult.Success<*>>(chapterResult)

            val chapters = repository.getChaptersByVolume(volumeId)
            assertEquals(1, chapters.size)
            assertEquals("Chapter 1", chapters.first().title)
        } finally {
            databaseRuntime.closeBlocking()
        }
    }
}
