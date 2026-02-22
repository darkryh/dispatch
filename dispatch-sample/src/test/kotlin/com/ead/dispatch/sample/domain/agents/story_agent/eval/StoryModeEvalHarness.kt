package com.ead.dispatch.sample.domain.agents.story_agent.eval

import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.entities.SessionRecord
import com.ead.dispatch.sample.data.db.entities.StoryChapterRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StorySceneRecord
import com.ead.dispatch.sample.data.db.entities.StoryVolumeRecord
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.SessionMode
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.agents.story_agent.KoogStoryAgent
import com.ead.dispatch.sample.domain.agents.story_agent.StoryRequest
import com.ead.dispatch.sample.domain.agents.story_agent.memory.policy.SummarizeNowStorySummarizationPolicy
import com.ead.dispatch.sample.domain.agents.story_agent.memory.service.StoryContinuityMemoryService
import com.ead.dispatch.sample.domain.agents.story_agent.policy.StoryTurnCheckpointProperties
import com.ead.dispatch.sample.domain.agents.story_agent.policy.isStoryDecisionToolName
import com.ead.dispatch.sample.domain.agents.tools.StoryDraftTools
import com.ead.dispatch.sample.domain.content.ChapterContentStore
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import kotlin.io.path.createTempDirectory

internal class StoryModeEvalHarness(
    private val perCaseTimeoutMillis: Long = DEFAULT_PER_CASE_TIMEOUT_MILLIS,
) : AutoCloseable {

    private val rootDir = createTempDirectory("dispatch-story-dokimos-eval")
    private val databaseRuntime = DatabaseRuntime(
        factory = DispatchDatabaseFactory(
            appName = "dispatch-test",
            appAuthor = "ead-test",
            dbFileName = "story-dokimos-${UUID.randomUUID()}.db",
            dataDirectoryOverride = rootDir,
        )
    )

    private val repository = StructuredIndexRepository(
        databaseRuntime = databaseRuntime,
        embeddingIndexService = EmbeddingIndexService(
            embedderProvider = ChatAgentEmbedder(),
            root = rootDir.resolve("embeddings"),
        ),
    )

    private val ragContextService = RagContextService(
        repository = repository,
        embeddingIndexService = EmbeddingIndexService(
            embedderProvider = ChatAgentEmbedder(),
            root = rootDir.resolve("embeddings-rag"),
        ),
    )

    private val storyAgent = KoogStoryAgent(
        repository = repository,
        ragContextService = ragContextService,
        continuityMemoryService = StoryContinuityMemoryService(repository),
        storyDraftTools = StoryDraftTools(
            repository = repository,
            contentStore = ChapterContentStore(rootDir.resolve("content")),
            continuityMemoryService = StoryContinuityMemoryService(repository),
            summarizationPolicy = SummarizeNowStorySummarizationPolicy,
        ),
    )

    fun run(cases: List<StoryEvalCase>): List<StoryEvalObservation> = runBlocking {
        buildList {
            cases.forEach { case ->
                val observation = try {
                    withTimeout(perCaseTimeoutMillis) {
                        val storyId = "story-dokimos-${UUID.randomUUID()}"
                        val session = seedSession(storyId, case.seedProfile)

                        case.warmupPrompts.forEach { warmupPrompt ->
                            val warmupResponse = storyAgent.run(
                                session = session,
                                input = StoryRequest(
                                    text = warmupPrompt,
                                    storyId = storyId,
                                ),
                            )
                            warmupResponse.value.collect { _ -> Unit }
                        }

                        val before = captureStoryState(storyId)

                        val response = storyAgent.run(
                            session = session,
                            input = StoryRequest(
                                text = case.prompt,
                                storyId = storyId,
                                fromDecisionPrompt = case.fromDecisionPrompt,
                            ),
                        )

                        val assistant = StringBuilder()
                        val toolCalls = mutableListOf<String>()
                        response.value.collect { frame ->
                            when (frame) {
                                is StreamFrame.Append -> assistant.append(frame.text)
                                is StreamFrame.ToolCall -> toolCalls += frame.name
                                is StreamFrame.End -> Unit
                            }
                        }

                        val after = captureStoryState(storyId)

                        val agentId = AIProvider.getStoryAgentId(session.id)
                        val latestCheckpoint = Storage.provider.getLatestCheckpoint(agentId)
                        val checkpointProperties = latestCheckpoint?.properties.orEmpty()

                        StoryEvalObservation(
                            case = case,
                            assistantText = assistant.toString().trim(),
                            toolCalls = toolCalls,
                            usedSelector = toolCalls.any { isStoryDecisionToolName(it) },
                            decisionPath = checkpointProperties.stringValue(StoryTurnCheckpointProperties.DECISION_PATH),
                            volumeDelta = after.volumeCount - before.volumeCount,
                            chapterDelta = after.chapterCount - before.chapterCount,
                            sceneDelta = after.sceneCount - before.sceneCount,
                            draftChanged = before.draftChecksums != after.draftChecksums,
                            writeToolCalls = checkpointProperties.intValue(StoryTurnCheckpointProperties.WRITE_TOOL_CALLS),
                            mutationCreateCount = checkpointProperties.intValue(StoryTurnCheckpointProperties.MUTATION_CREATE_COUNT),
                            mutationUpdateCount = checkpointProperties.intValue(StoryTurnCheckpointProperties.MUTATION_UPDATE_COUNT),
                            mutationDeleteCount = checkpointProperties.intValue(StoryTurnCheckpointProperties.MUTATION_DELETE_COUNT),
                        )
                    }
                } catch (_: TimeoutCancellationException) {
                    throw AssertionError(
                        "Story eval case timed out after ${perCaseTimeoutMillis}ms: id=${case.id}, prompt=\"${case.prompt}\""
                    )
                }
                add(observation)
            }
        }
    }

    override fun close() {
        databaseRuntime.closeBlocking()
        rootDir.toFile().deleteRecursively()
    }

    private suspend fun seedSession(storyId: String, profile: StoryEvalSeedProfile): Session {
        val now = Clock.System.now().toEpochMilliseconds()
        repository.upsertSession(
            SessionRecord(
                id = storyId,
                profile = SessionRecord.SessionProfile(
                    title = "Story Dokimos Eval",
                    mode = SessionMode.CHAT_STORY,
                ),
                createdAt = now,
                updatedAt = now,
                stats = SessionRecord.SessionStats(messageCount = 0),
                metadata = emptyMap(),
            ),
        )

        repository.upsertStory(
            StoryRecord(
                id = storyId,
                sessionId = storyId,
                title = "Dokimos Story",
                genre = "Fantasy",
                setting = "Ruined port city",
                plotOutline = "A drifting cast protects unstable route maps.",
                status = ContentStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            ),
        )

        when (profile) {
            StoryEvalSeedProfile.EMPTY_STRUCTURE -> Unit
            StoryEvalSeedProfile.BASIC_STRUCTURE -> seedBasicStructure(storyId, now)
            StoryEvalSeedProfile.RICH_STRUCTURE -> {
                seedBasicStructure(storyId, now)
                seedRichStoryContext(storyId, now)
            }
        }

        return Session(
            id = storyId,
            title = "Story Dokimos Eval",
            updatedAt = Clock.System.now(),
            messageCount = 0,
        )
    }

    private suspend fun seedBasicStructure(storyId: String, now: Long) {
        val volume = StoryVolumeRecord(
            id = "vol-1-$storyId",
            storyId = storyId,
            number = 1,
            title = "Volume 1",
            keyEvents = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertVolume(volume)

        val chapter = StoryChapterRecord(
            id = "ch-1-$storyId",
            volumeId = volume.id,
            number = 1,
            title = "Chapter 1",
            summary = "Dark arrives at the ruined harbor.",
            keyEvents = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertChapter(chapter)

        val scene = StorySceneRecord(
            id = "sc-1-$storyId",
            chapterId = chapter.id,
            number = 1,
            title = "Arrival",
            summary = "He reaches the docks before dawn.",
            keyEvents = emptyList(),
            createdAt = now,
            updatedAt = now,
        )
        repository.upsertScene(scene)
    }

    private suspend fun seedRichStoryContext(storyId: String, now: Long) {
        repository.insertStoryCharacter(
            StoryCharacterRecord(
                id = "char-dark-$storyId",
                storyId = storyId,
                name = "Dark",
                description = "Guarded courier with high empathy under pressure.",
                traits = listOf("guarded", "empathetic", "strategic"),
                roles = listOf("protagonist"),
                goal = "Protect unstable routes.",
                motivation = "Atonement for a failed rescue.",
                createdAt = now,
            ),
        )
        repository.insertStoryCharacter(
            StoryCharacterRecord(
                id = "char-mira-$storyId",
                storyId = storyId,
                name = "Mira Venn",
                description = "Cartographer obsessed with route truth.",
                traits = listOf("precise", "skeptical"),
                roles = listOf("ally"),
                goal = "Decode route corruption.",
                motivation = "Recover lost family records.",
                createdAt = now,
            ),
        )
    }

    private suspend fun captureStoryState(storyId: String): StoryStateSnapshot {
        val volumes = repository.getVolumesByStory(storyId)
        val chapters = volumes.flatMap { repository.getChaptersByVolume(it.id) }
        val scenes = chapters.flatMap { repository.getScenesByChapter(it.id) }
        val checksums = chapters.associate { chapter ->
            chapter.id to repository.getStoryDraftCurrentByChapterId(chapter.id)?.checksum
        }
        return StoryStateSnapshot(
            volumeCount = volumes.size,
            chapterCount = chapters.size,
            sceneCount = scenes.size,
            draftChecksums = checksums,
        )
    }

    private data class StoryStateSnapshot(
        val volumeCount: Int,
        val chapterCount: Int,
        val sceneCount: Int,
        val draftChecksums: Map<String, String?>,
    )

    private companion object {
        private const val DEFAULT_PER_CASE_TIMEOUT_MILLIS = 120_000L
    }
}

private fun Map<String, JsonElement>.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

private fun Map<String, JsonElement>.intValue(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
