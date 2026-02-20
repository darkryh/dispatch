package com.ead.dispatch.sample.domain.agents.chat_agent.eval

import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.entities.SessionRecord
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StoryWorldRuleRecord
import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.SessionMode
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.AIProvider
import com.ead.dispatch.sample.domain.Storage
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatAgentEmbedder
import com.ead.dispatch.sample.domain.agents.chat_agent.ChatRequest
import com.ead.dispatch.sample.domain.agents.chat_agent.KoogChatAgent
import com.ead.dispatch.sample.domain.agents.chat_agent.policy.ChatTurnCheckpointProperties
import com.ead.dispatch.sample.domain.embedding.EmbeddingIndexService
import com.ead.dispatch.sample.domain.embedding.RagContextService
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID
import kotlin.io.path.createTempDirectory

internal class ChatModeEvalHarness : AutoCloseable {

    private val rootDir = createTempDirectory("dispatch-chat-dokimos-eval")
    private val databaseRuntime = DatabaseRuntime(
        factory = DispatchDatabaseFactory(
            appName = "dispatch-test",
            appAuthor = "ead-test",
            dbFileName = "chat-dokimos-${UUID.randomUUID()}.db",
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

    private val chatAgent = KoogChatAgent(
        repository = repository,
        ragContextService = ragContextService,
    )

    fun run(cases: List<ChatEvalCase>): List<ChatEvalObservation> = runBlocking {
        cases.map { case ->
            val storyId = "dokimos-${UUID.randomUUID()}"
            val session = seedSession(storyId, case.seedProfile)

            case.warmupPrompts.forEach { warmupPrompt ->
                val warmupResponse = chatAgent.run(
                    session = session,
                    input = ChatRequest(
                        text = warmupPrompt,
                        storyId = storyId,
                    )
                )
                warmupResponse.value.collect { _ -> Unit }
            }

            val beforeCount = entityCount(storyId)

            val response = chatAgent.run(
                session = session,
                input = ChatRequest(
                    text = case.prompt,
                    storyId = storyId,
                    fromDecisionPrompt = case.fromDecisionPrompt,
                )
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

            val afterCount = entityCount(storyId)
            val assistantText = assistant.toString().trim()
            val latestCheckpoint = Storage.provider.getLatestCheckpoint(AIProvider.getChatAgentId(session.id))
            val checkpointProperties = latestCheckpoint?.properties.orEmpty()
            val preferenceSaveExecuted = (checkpointProperties[ChatTurnCheckpointProperties.PREFERENCE_SAVE_EXECUTED] as? JsonPrimitive)
                ?.content
                ?.trim()
                ?.lowercase()
                ?.let { raw ->
                    when (raw) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                }
            val preferenceSaveSkippedReason = (checkpointProperties[ChatTurnCheckpointProperties.PREFERENCE_SAVE_SKIPPED_REASON] as? JsonPrimitive)
                ?.content

            ChatEvalObservation(
                case = case,
                assistantText = assistantText,
                toolCalls = toolCalls,
                usedSelector = toolCalls.any { it.equals("requestUserChoice", ignoreCase = true) },
                wroteState = afterCount > beforeCount,
                wordCount = countWords(assistantText),
                preferenceSaveExecuted = preferenceSaveExecuted,
                preferenceSaveSkippedReason = preferenceSaveSkippedReason,
            )
        }
    }

    override fun close() {
        databaseRuntime.closeBlocking()
        rootDir.toFile().deleteRecursively()
    }

    private suspend fun seedSession(storyId: String, profile: ChatEvalSeedProfile): Session {
        val now = Clock.System.now().toEpochMilliseconds()

        repository.upsertSession(
            SessionRecord(
                id = storyId,
                profile = SessionRecord.SessionProfile(
                    title = "Dokimos Eval Session",
                    mode = SessionMode.CHAT,
                ),
                createdAt = now,
                updatedAt = now,
                stats = SessionRecord.SessionStats(messageCount = 0),
                metadata = emptyMap(),
            )
        )

        repository.upsertStory(
            StoryRecord(
                id = storyId,
                sessionId = storyId,
                title = StoryRecord.PLACEHOLDER_TITLE,
                genre = "Fantasy",
                setting = "Port city",
                plotOutline = "A drifting cast confronts a hidden map network.",
                status = ContentStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )
        )

        if (profile == ChatEvalSeedProfile.RICH_CONTEXT) {
            seedRichStoryContext(storyId, now)
        }

        return Session(
            id = storyId,
            title = "Dokimos Eval Session",
            updatedAt = Clock.System.now(),
            messageCount = 0,
        )
    }

    private suspend fun seedRichStoryContext(storyId: String, now: Long) {
        val darkId = "char-dark-$storyId"
        val miraId = "char-mira-$storyId"
        val tavernId = "loc-salvage-$storyId"
        val mainArcId = "arc-main-$storyId"
        val memoryRuleId = "rule-memory-map-$storyId"

        repository.insertStoryCharacter(
            StoryCharacterRecord(
                id = darkId,
                storyId = storyId,
                name = "Dark",
                description = "Guarded courier with strong survival instincts and hidden empathy.",
                traits = listOf("guarded", "strategic", "loyal"),
                roles = listOf("protagonist"),
                goal = "Protect the map routes and keep allies alive.",
                motivation = "Atonement for a failed rescue.",
                createdAt = now,
            )
        )
        repository.insertStoryCharacter(
            StoryCharacterRecord(
                id = miraId,
                storyId = storyId,
                name = "Mira Venn",
                description = "Analytical cartographer focused on route truth and map integrity.",
                traits = listOf("precise", "skeptical"),
                roles = listOf("supporting", "informant"),
                goal = "Uncover who altered the ancient routes.",
                motivation = "Recover family records lost in the collapse.",
                createdAt = now,
            )
        )

        repository.upsertLocation(
            StoryLocationRecord(
                id = tavernId,
                storyId = storyId,
                profile = StoryLocationRecord.LocationProfile(
                    name = "Rook's Salvage Tavern",
                    description = "A neutral hub where smugglers, scouts, and archivists trade intel.",
                ),
                tags = listOf("urban", "neutral-ground", "high-tension"),
                createdAt = now,
            )
        )

        repository.upsertArc(
            StoryArcRecord(
                id = mainArcId,
                storyId = storyId,
                scopeType = ArcScope.STORY,
                title = "Map Fragment War",
                summary = "Dark and allies race to decode a living map before rival factions weaponize it.",
                status = ContentStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryWorldRule(
            StoryWorldRuleRecord(
                id = memoryRuleId,
                storyId = storyId,
                title = "Maps Remember Intent",
                description = "Routes shift based on emotional intent; deception creates unstable paths.",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun entityCount(storyId: String): Int {
        return repository.getStoryCharacters(storyId).size +
            repository.getLocationsByStory(storyId).size +
            repository.getArcsByStory(storyId).size +
            repository.getWorldRulesByStory(storyId).size +
            repository.getCulturesByStory(storyId).size +
            repository.getEventsByStory(storyId).size +
            repository.getOrganizationsByStory(storyId).size +
            repository.getRelationshipsByStory(storyId).size +
            repository.getLocationFeaturesByStory(storyId).size +
            repository.getArtifactsByStory(storyId).size +
            repository.getTimelineEntriesByStory(storyId).size
    }
}
