package com.ead.dispatch.sample.domain.agents.chat_agent.eval

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.ead.dispatch.sample.data.db.entities.DatabaseRuntime
import com.ead.dispatch.sample.data.db.entities.DispatchDatabaseFactory
import com.ead.dispatch.sample.data.db.entities.SessionRecord
import com.ead.dispatch.sample.data.db.entities.StoryArtifactRecord
import com.ead.dispatch.sample.data.db.entities.StoryArcRecord
import com.ead.dispatch.sample.data.db.entities.StoryCharacterRecord
import com.ead.dispatch.sample.data.db.entities.StoryCultureRecord
import com.ead.dispatch.sample.data.db.entities.StoryEventRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationFeatureRecord
import com.ead.dispatch.sample.data.db.entities.StoryLocationRecord
import com.ead.dispatch.sample.data.db.entities.StoryOrganizationRecord
import com.ead.dispatch.sample.data.db.entities.StoryRelationshipRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.db.entities.StoryTimelineEntryRecord
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
import kotlinx.serialization.json.Json
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

            case.injectedHistoryProfile?.let { profile ->
                injectChatHistoryCheckpoint(
                    session = session,
                    storyId = storyId,
                    profile = profile,
                )
                if (
                    profile == ChatEvalInjectedHistoryProfile.HEAVY ||
                    profile == ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_HEAVY_REGRESSION
                ) {
                    validateHeavyContext(storyId)
                }
            }

            val beforeCount = entityCount(storyId)

            val response = chatAgent.run(
                session = session,
                input = ChatRequest(
                    text = case.prompt,
                    storyId = storyId,
                    fromDecisionPrompt = case.fromDecisionPrompt,
                    decisionContext = case.decisionContext,
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
        val talId = "char-tal-$storyId"
        val tavernId = "loc-salvage-$storyId"
        val relayId = "loc-echo-relay-$storyId"
        val mainArcId = "arc-main-$storyId"
        val fractureArcId = "arc-fracture-$storyId"
        val memoryRuleId = "rule-memory-map-$storyId"
        val guiltRuleId = "rule-guilt-resonance-$storyId"
        val brokersOrgId = "org-channel-brokers-$storyId"
        val quietLedgerOrgId = "org-quiet-ledger-$storyId"
        val eventForgedLogsId = "evt-forged-logs-$storyId"
        val eventTavernFalloutId = "evt-tavern-fallout-$storyId"
        val cultureId = "culture-drift-monastics-$storyId"
        val featureTavernId = "feature-signal-cellar-$storyId"
        val featureRelayId = "feature-memory-spire-$storyId"
        val artifactId = "artifact-saltglass-compass-$storyId"
        val timeline1Id = "tl-port-rumor-$storyId"
        val timeline2Id = "tl-tavern-fallout-$storyId"
        val rel1Id = "rel-dark-mira-$storyId"
        val rel2Id = "rel-brokers-mira-$storyId"

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
        repository.insertStoryCharacter(
            StoryCharacterRecord(
                id = talId,
                storyId = storyId,
                name = "Tal Ren",
                description = "Restless scout who pushes for fast action and distrusts institutions.",
                traits = listOf("impatient", "resourceful", "suspicious"),
                roles = listOf("supporting", "rival-ally"),
                goal = "Break the hidden route cartel before another convoy disappears.",
                motivation = "A sibling vanished on a forged route.",
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
        repository.upsertLocation(
            StoryLocationRecord(
                id = relayId,
                storyId = storyId,
                profile = StoryLocationRecord.LocationProfile(
                    name = "Echo Relay",
                    description = "An old navigation tower where memory bleed distorts active maps.",
                ),
                tags = listOf("signal-node", "volatile", "plot-pivot"),
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
        repository.upsertArc(
            StoryArcRecord(
                id = fractureArcId,
                storyId = storyId,
                scopeType = ArcScope.STORY,
                title = "Alliance Fracture",
                summary = "Dark and Mira split over strategy as route manipulation spreads.",
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
        repository.insertStoryWorldRule(
            StoryWorldRuleRecord(
                id = guiltRuleId,
                storyId = storyId,
                title = "Unresolved Guilt Echoes",
                description = "Map fragments amplify unresolved guilt into false route signals.",
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryCulture(
            StoryCultureRecord(
                id = cultureId,
                storyId = storyId,
                name = "Drift Monastics",
                description = "Navigators trained to suppress emotional leakage while charting living maps.",
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryEvent(
            StoryEventRecord(
                id = eventTavernFalloutId,
                storyId = storyId,
                name = "Rook's Tavern Fallout",
                description = "A negotiation collapses when forged route evidence implicates a trusted broker.",
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.insertStoryEvent(
            StoryEventRecord(
                id = eventForgedLogsId,
                storyId = storyId,
                name = "Misdirected Supply Run",
                description = "Forged route logs divert a supply convoy into contested waters.",
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryOrganization(
            StoryOrganizationRecord(
                id = brokersOrgId,
                storyId = storyId,
                name = "Channel Brokers",
                description = "Market middlemen who profit from route instability and scarcity spikes.",
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.insertStoryOrganization(
            StoryOrganizationRecord(
                id = quietLedgerOrgId,
                storyId = storyId,
                name = "The Quiet Ledger",
                description = "A covert accounting network suspected of coordinating forged route traffic.",
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryLocationFeature(
            StoryLocationFeatureRecord(
                id = featureTavernId,
                storyId = storyId,
                locationId = tavernId,
                name = "Signal Cellar",
                description = "A backroom where route logs and smuggler ledgers are exchanged.",
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.insertStoryLocationFeature(
            StoryLocationFeatureRecord(
                id = featureRelayId,
                storyId = storyId,
                locationId = relayId,
                name = "Memory Spire",
                description = "A resonance column that intensifies intent-based distortions in maps.",
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryArtifact(
            StoryArtifactRecord(
                id = artifactId,
                storyId = storyId,
                name = "Saltglass Compass",
                description = "An unstable compass that reacts to guilt-tainted route signals.",
                ownerId = miraId,
                ownerType = "CHARACTER",
                locationId = relayId,
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryTimelineEntry(
            StoryTimelineEntryRecord(
                id = timeline1Id,
                storyId = storyId,
                title = "Port Rumor Spike",
                description = "Smugglers report inconsistent routes and rising broker prices.",
                orderIndex = 1,
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.insertStoryTimelineEntry(
            StoryTimelineEntryRecord(
                id = timeline2Id,
                storyId = storyId,
                title = "Tavern Fallout",
                description = "Trust collapses after forged logs surface during negotiations at Rook's.",
                orderIndex = 2,
                createdAt = now,
                updatedAt = now,
            )
        )

        repository.insertStoryRelationship(
            StoryRelationshipRecord(
                id = rel1Id,
                storyId = storyId,
                subjectId = darkId,
                subjectType = "CHARACTER",
                objectId = miraId,
                objectType = "CHARACTER",
                relation = "strained alliance",
                notes = "Trust weakened after the rescue failure.",
                createdAt = now,
                updatedAt = now,
            )
        )
        repository.insertStoryRelationship(
            StoryRelationshipRecord(
                id = rel2Id,
                storyId = storyId,
                subjectId = brokersOrgId,
                subjectType = "ORGANIZATION",
                objectId = miraId,
                objectType = "CHARACTER",
                relation = "surveils",
                notes = "Brokers track Mira's archive requests for route anomalies.",
                createdAt = now,
                updatedAt = now,
            )
        )
    }

    private suspend fun validateHeavyContext(storyId: String) {
        val counts = HeavyContextCounts(
            characters = repository.getStoryCharacters(storyId).size,
            locations = repository.getLocationsByStory(storyId).size,
            arcs = repository.getArcsByStory(storyId).size,
            worldRules = repository.getWorldRulesByStory(storyId).size,
            cultures = repository.getCulturesByStory(storyId).size,
            events = repository.getEventsByStory(storyId).size,
            organizations = repository.getOrganizationsByStory(storyId).size,
            relationships = repository.getRelationshipsByStory(storyId).size,
            locationFeatures = repository.getLocationFeaturesByStory(storyId).size,
            artifacts = repository.getArtifactsByStory(storyId).size,
            timelineEntries = repository.getTimelineEntriesByStory(storyId).size,
        )

        val failures = buildList {
            if (counts.characters < 3) add("characters>=3 (actual=${counts.characters})")
            if (counts.locations < 2) add("locations>=2 (actual=${counts.locations})")
            if (counts.arcs < 2) add("arcs>=2 (actual=${counts.arcs})")
            if (counts.worldRules < 2) add("worldRules>=2 (actual=${counts.worldRules})")
            if (counts.cultures < 1) add("cultures>=1 (actual=${counts.cultures})")
            if (counts.events < 2) add("events>=2 (actual=${counts.events})")
            if (counts.organizations < 2) add("organizations>=2 (actual=${counts.organizations})")
            if (counts.relationships < 2) add("relationships>=2 (actual=${counts.relationships})")
            if (counts.locationFeatures < 2) add("locationFeatures>=2 (actual=${counts.locationFeatures})")
            if (counts.artifacts < 1) add("artifacts>=1 (actual=${counts.artifacts})")
            if (counts.timelineEntries < 2) add("timelineEntries>=2 (actual=${counts.timelineEntries})")
        }

        check(failures.isEmpty()) {
            "Heavy eval context is underbuilt for storyId=$storyId: ${failures.joinToString(", ")}"
        }
    }

    private data class HeavyContextCounts(
        val characters: Int,
        val locations: Int,
        val arcs: Int,
        val worldRules: Int,
        val cultures: Int,
        val events: Int,
        val organizations: Int,
        val relationships: Int,
        val locationFeatures: Int,
        val artifacts: Int,
        val timelineEntries: Int,
    )

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

    private suspend fun injectChatHistoryCheckpoint(
        session: Session,
        storyId: String,
        profile: ChatEvalInjectedHistoryProfile,
    ) {
        val agentId = AIProvider.getChatAgentId(session.id)
        val latest = Storage.provider.getLatestCheckpoint(agentId)
        val history = buildInjectedHistory(storyId, profile)
        val placeholderInput = ChatRequest(
            text = "history-seeded-placeholder",
            storyId = storyId,
        )

        val checkpoint = AgentCheckpointData(
            checkpointId = latest?.checkpointId ?: "seed-${UUID.randomUUID()}",
            createdAt = Clock.System.now(),
            nodePath = "$agentId/chat-mode.planner/chat-intent-flow",
            lastInput = Json.encodeToJsonElement(ChatRequest.serializer(), placeholderInput),
            messageHistory = history,
            version = latest?.version?.plus(1) ?: 0L,
            properties = latest?.properties ?: emptyMap(),
        )

        Storage.provider.saveCheckpoint(agentId, checkpoint)
    }

    private fun buildInjectedHistory(
        storyId: String,
        profile: ChatEvalInjectedHistoryProfile,
    ): List<Message> = when (profile) {
        ChatEvalInjectedHistoryProfile.LIGHT -> buildLightInjectedHistory(storyId)
        ChatEvalInjectedHistoryProfile.HEAVY -> buildHeavyInjectedHistory(storyId)
        ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_LIGHT_REGRESSION -> buildImportantFitLightRegressionHistory(storyId)
        ChatEvalInjectedHistoryProfile.IMPORTANT_FIT_HEAVY_REGRESSION -> buildImportantFitHeavyRegressionHistory(storyId)
        ChatEvalInjectedHistoryProfile.PROTAGONIST_BEST_FIT_REGRESSION -> buildProtagonistBestFitRegressionHistory(storyId)
    }

    private fun buildLightInjectedHistory(storyId: String): List<Message> {
        val callId = "tool-light-create-char"
        return listOf(
            user("yo, it is me again. continue the map story."),
            assistant("We have Dark and Mira, the living-map conflict, and rising trust issues around route tampering."),
            user("Create a side courier named Fen and save it."),
            toolCall(callId, "createCharacter", """{"storyId":"$storyId","request":{"name":"Fen","roles":["supporting"]}}"""),
            toolResult(
                callId,
                "createCharacter",
                """{"type":"success","data":{"action":"create","entity":"CHARACTER","storyId":"$storyId","entityId":"char-fen","summary":"Created Fen (support courier)."},"message":"Character created."}"""
            ),
            assistant("Done. Fen is added as a support courier tied to the port routes."),
            user("Dark and Mira are getting harder to align, and I think someone else may need to take point soon."),
            assistant("Understood. That would affect the story's center of gravity, alliances, and future arc direction."),
        )
    }

    private fun buildHeavyInjectedHistory(storyId: String): List<Message> {
        val c1 = "tool-heavy-event"
        val c2 = "tool-heavy-org"
        val c3 = "tool-heavy-arc"
        val c4 = "tool-heavy-timeline"
        val c5 = "tool-heavy-character"
        return listOf(
            user("yo, it is me. let's continue from the tavern fallout."),
            assistant("Current state: Dark and Mira are strained, route forgeries are escalating, and the map ethics conflict is destabilizing alliances."),
            user("Summarize what changed after the rescue failure."),
            assistant("Dark lost leverage after hesitation during the failed rescue, Mira now distrusts tactical delays, and multiple factions are exploiting the route confusion."),
            user("Create an event where forged logs misdirect a supply run and save it."),
            toolCall(c1, "createEvent", """{"storyId":"$storyId","request":{"name":"Misdirected Supply Run","description":"Forged route logs divert supplies into contested waters."}}"""),
            toolResult(
                c1,
                "createEvent",
                """{"type":"success","data":{"action":"create","entity":"EVENT","storyId":"$storyId","entityId":"evt-forged-logs","summary":"Created event: Misdirected Supply Run."},"message":"Event created."}"""
            ),
            assistant("Done. The forged-log supply diversion now directly escalates trust and scarcity pressure."),
            user("Nice. Keep the pressure grounded in logistics, not magic spectacle."),
            assistant("Understood. I will keep consequences practical: shortages, reroutes, and faction leverage first."),
            user("Create an organization that profits from route instability and save it."),
            toolCall(c2, "createOrganization", """{"storyId":"$storyId","request":{"name":"Channel Brokers","description":"Middlemen who exploit unstable route markets."}}"""),
            toolResult(
                c2,
                "createOrganization",
                """{"type":"success","data":{"action":"create","entity":"ORGANIZATION","storyId":"$storyId","entityId":"org-channel-brokers","summary":"Created organization: Channel Brokers."},"message":"Organization created."}"""
            ),
            assistant("Done. Channel Brokers now fit as opportunists, but not yet confirmed as the central force behind the forgeries."),
            user("yeah keep it grounded tho, not too big yet."),
            assistant("Understood. I'll keep them as pressure-amplifying operators, not a world-scale villain."),
            user("Good. Do not make them the mastermind yet."),
            assistant("Agreed. I will preserve ambiguity and keep them as an opportunist layer for now."),
            user("Add an arc for the alliance fracture and save it."),
            toolCall(c3, "createArc", """{"storyId":"$storyId","request":{"title":"Alliance Fracture","summary":"Dark and Mira split over strategy as route manipulation spreads."}}"""),
            toolResult(
                c3,
                "createArc",
                """{"type":"success","data":{"action":"create","entity":"ARC","storyId":"$storyId","entityId":"arc-alliance-fracture","summary":"Created arc: Alliance Fracture."},"message":"Arc created."}"""
            ),
            assistant("Done. The story now has an explicit fracture arc, but the mastermind force behind the forged logs is still unresolved and will shape future continuity."),
            user("Add a timeline entry for when the tavern negotiations collapse, then save it."),
            toolCall(c4, "createTimelineEntry", """{"storyId":"$storyId","request":{"title":"Tavern Negotiation Collapse","description":"Forged logs surface at Rook's Salvage Tavern and fracture trust between allies.","orderIndex":3}}"""),
            toolResult(
                c4,
                "createTimelineEntry",
                """{"type":"success","data":{"action":"create","entity":"TIMELINE","storyId":"$storyId","entityId":"tl-tavern-collapse","summary":"Created timeline entry: Tavern Negotiation Collapse."},"message":"Timeline entry created."}"""
            ),
            assistant("Done. The timeline now makes the tavern fallout explicit as the trigger for the fracture arc."),
            user("wait, not 'mastermind force' vibes from before. more like coordinated pressure we still can't map."),
            assistant("Good correction. I'll frame the hidden actor as a coordination layer causing pressure, not a revealed mastermind identity."),
            user("Create a supporting scout tied to Tal who can report port traffic patterns and save it."),
            toolCall(c5, "createCharacter", """{"storyId":"$storyId","request":{"name":"Iven Silt","roles":["supporting","scout"],"description":"Port scout who tracks convoy anomalies for Tal."}}"""),
            toolResult(
                c5,
                "createCharacter",
                """{"type":"success","data":{"action":"create","entity":"CHARACTER","storyId":"$storyId","entityId":"char-iven-silt","summary":"Created character: Iven Silt (supporting scout)."},"message":"Character created."}"""
            ),
            assistant("Done. Iven Silt now gives Tal a grounded reporting channel for convoy movement and route anomalies."),
            user("Quick recap before we add anything foundational."),
            assistant("Recap: trust fracture is active, route forgery pressure is causing concrete shortages, Channel Brokers benefit but remain a secondary suspect, and the unseen coordinating force is still open."),
            user("I want the next additions to feel coherent with the trust fracture and map ethics conflict, not random."),
            assistant("Understood. Foundational additions now need to fit continuity because they can define who carries the conflict and what the story is actually about going forward."),
            user("Also keep the tone morally gray and practical; no clean heroes."),
            assistant("Noted. I will favor compromised motives, tradeoffs, and practical consequences over heroic clarity."),
        )
    }

    private fun buildProtagonistBestFitRegressionHistory(storyId: String): List<Message> {
        val c1 = "tool-reg-lyra"
        val c2a = "tool-reg-kaelen-bad"
        val c2b = "tool-reg-kaelen-good"
        val c3 = "tool-reg-zephyr"
        return listOf(
            user("okay, create new 3 random characters. be creative."),
            assistant("I'll create three creative characters for your story and save them now."),
            toolCall(c1, "createCharacter", """{"storyId":"$storyId","request":{"name":"Lyra Silversong","roles":["protagonist","explorer"],"description":"Celestial cartographer mapping constellations that may be gateways.","quirks":["Talks to constellations","Collects unusual inks","Carries a star-chart journal"]}}"""),
            toolResult(
                c1,
                "createCharacter",
                """{"type":"success","data":{"action":"create","entity":"CHARACTER","storyId":"$storyId","entityId":"char-lyra","summary":"Created Lyra Silversong."},"message":"Character created."}"""
            ),
            assistant("Created Lyra Silversong, a celestial cartographer protagonist with a discovery-driven arc."),
            toolCall(c2a, "createCharacter", """{"storyId":"$storyId","request":{"name":"Kaelen Stoneheart","roles":["antagonist","craftsman"],"description":"Memory-smith forging artifacts from memories.","quirks":"Taps objects with a silver hammer before touching them"}}"""),
            toolResult(
                c2a,
                "createCharacter",
                """{"type":"error","error":"Expected JsonArray for quirks; got JsonLiteral","field":"quirks"}"""
            ),
            assistant("Tool payload failed on quirks format. I'll fix the quirks field and retry."),
            toolCall(c2b, "createCharacter", """{"storyId":"$storyId","request":{"name":"Kaelen Stoneheart","roles":["antagonist","craftsman"],"description":"Memory-smith forging artifacts from memories.","quirks":["Taps objects with a silver hammer before touching them","Always wears leather gloves","Speaks in forging metaphors"]}}"""),
            toolResult(
                c2b,
                "createCharacter",
                """{"type":"success","data":{"action":"create","entity":"CHARACTER","storyId":"$storyId","entityId":"char-kaelen","summary":"Created Kaelen Stoneheart."},"message":"Character created."}"""
            ),
            assistant("Created Kaelen Stoneheart, a morally ambiguous memory-smith antagonist tied to stolen memories and artifacts."),
            toolCall(c3, "createCharacter", """{"storyId":"$storyId","request":{"name":"Zephyr the Unbound","roles":["mentor","wildcard"],"description":"Reality drifter who perceives branching timelines.","quirks":["Speaks about past and future interchangeably","Leaves faint afterimages","Collects echoes of significant moments"]}}"""),
            toolResult(
                c3,
                "createCharacter",
                """{"type":"success","data":{"action":"create","entity":"CHARACTER","storyId":"$storyId","entityId":"char-zephyr","summary":"Created Zephyr the Unbound."},"message":"Character created."}"""
            ),
            assistant("Created Zephyr the Unbound, a mentor-wildcard who perceives multiple timelines and branching possibilities."),
            assistant("Summary: Lyra (celestial gateway discovery), Kaelen (memory-artifact theft), and Zephyr (timeline perception) create a story space centered on reality, memory, and possibility."),
            user("okay, can you create another protagonist and you can decide what's the best fit for it?"),
            assistant("I'll create another protagonist that fits your existing cast and themes."),
            assistant("I can connect the new lead to Lyra's celestial discoveries, Kaelen's memory artifacts, and Zephyr's timeline perception so the role fits the current story direction."),
        )
    }

    private fun buildImportantFitLightRegressionHistory(storyId: String): List<Message> {
        val c1 = "tool-imp-light-event"
        val c2 = "tool-imp-light-artifact"
        return listOf(
            user("yo, continue the route-forgery story."),
            assistant("Current pressure: route failures are spreading, trust is unstable, and nobody has a reliable stabilizing method yet."),
            user("Create an event where a convoy gets lost because a forged route shifts mid-crossing."),
            toolCall(c1, "createEvent", """{"storyId":"$storyId","request":{"name":"Mid-Crossing Route Shift","description":"A convoy is redirected mid-crossing after forged route markings resonate."}}"""),
            toolResult(
                c1,
                "createEvent",
                """{"type":"success","data":{"action":"create","entity":"EVENT","storyId":"$storyId","entityId":"evt-mid-shift","summary":"Created event: Mid-Crossing Route Shift."},"message":"Event created."}"""
            ),
            assistant("Done. That event makes the route failures feel immediate and costly."),
            user("Create one experimental artifact people tried already, but keep it a failed attempt."),
            toolCall(c2, "createArtifact", """{"storyId":"$storyId","request":{"name":"Tin Resonance Pin","description":"A cheap resonance pin used by pilots; it fails under forged route interference."}}"""),
            toolResult(
                c2,
                "createArtifact",
                """{"type":"success","data":{"action":"create","entity":"ARTIFACT","storyId":"$storyId","entityId":"artifact-tin-pin","summary":"Created artifact: Tin Resonance Pin."},"message":"Artifact created."}"""
            ),
            assistant("Done. We now have a failed stabilizer attempt, which raises the stakes for whatever working artifact comes next."),
            user("nice. keep it practical, like people are trying to solve logistics not destiny."),
            assistant("Understood. I will keep the solutions grounded in trade routes, reliability, and survival pressure."),
        )
    }

    private fun buildImportantFitHeavyRegressionHistory(storyId: String): List<Message> {
        val c1 = "tool-imp-heavy-event"
        val c2 = "tool-imp-heavy-org"
        val c3 = "tool-imp-heavy-artifact-fail"
        val c4 = "tool-imp-heavy-timeline"
        val c5 = "tool-imp-heavy-location-feature"
        return listOf(
            user("yo, continue from the relay and tavern fallout."),
            assistant("Current state: trust fracture is active, forged routes are destabilizing shipments, and nobody has a proven way to anchor route integrity."),
            user("Quick summary of what's driving the conflict right now."),
            assistant("Dark and Mira are split on strategy, opportunist brokers profit from instability, and the hidden coordination layer is pushing failures faster than the cast can respond."),
            user("Create an event where a rescue route collapses after a forged signal cascades."),
            toolCall(c1, "createEvent", """{"storyId":"$storyId","request":{"name":"Cascading Signal Collapse","description":"A rescue route fails after forged relay signals propagate across linked markers."}}"""),
            toolResult(
                c1,
                "createEvent",
                """{"type":"success","data":{"action":"create","entity":"EVENT","storyId":"$storyId","entityId":"evt-cascade-collapse","summary":"Created event: Cascading Signal Collapse."},"message":"Event created."}"""
            ),
            assistant("Done. That escalates the conflict from mistrust to visible humanitarian cost."),
            user("Good. Keep consequences practical and ugly, not mythic."),
            assistant("Agreed. I will frame effects as shortages, reroutes, and survival tradeoffs first."),
            user("Create a broker shell group that moves parts and rumor traffic during the failures."),
            toolCall(c2, "createOrganization", """{"storyId":"$storyId","request":{"name":"Lantern Freight Circle","description":"A broker shell group that profits by redistributing relay parts and rumor traffic during route failures."}}"""),
            toolResult(
                c2,
                "createOrganization",
                """{"type":"success","data":{"action":"create","entity":"ORGANIZATION","storyId":"$storyId","entityId":"org-lantern-freight-circle","summary":"Created organization: Lantern Freight Circle."},"message":"Organization created."}"""
            ),
            assistant("Done. Lantern Freight Circle fits as an opportunist layer without revealing the hidden coordinator."),
            user("yeah, not mastermind. just pressure merchants."),
            assistant("Understood. They stay as amplifiers of instability, not the final answer."),
            user("Create a failed anchoring artifact pilots tried after the relay incidents."),
            toolCall(c3, "createArtifact", """{"storyId":"$storyId","request":{"name":"Brass Route Nail","description":"A portable anchoring nail meant to pin route signals, but it overreacts to forged resonance and drifts."}}"""),
            toolResult(
                c3,
                "createArtifact",
                """{"type":"success","data":{"action":"create","entity":"ARTIFACT","storyId":"$storyId","entityId":"artifact-brass-route-nail","summary":"Created artifact: Brass Route Nail."},"message":"Artifact created."}"""
            ),
            assistant("Done. The failed anchoring nail now shows the cast has attempted practical fixes and failed."),
            user("Add a timeline entry for when relay crews stop trusting local markers."),
            toolCall(c4, "createTimelineEntry", """{"storyId":"$storyId","request":{"title":"Marker Trust Break","description":"Relay crews stop trusting local route markers after repeated forged cascades.","orderIndex":4}}"""),
            toolResult(
                c4,
                "createTimelineEntry",
                """{"type":"success","data":{"action":"create","entity":"TIMELINE","storyId":"$storyId","entityId":"tl-marker-trust-break","summary":"Created timeline entry: Marker Trust Break."},"message":"Timeline entry created."}"""
            ),
            assistant("Done. That makes route trust collapse explicit in chronology."),
            user("Create a relay feature where crews test signal dampening rigs."),
            toolCall(c5, "createLocationFeature", """{"storyId":"$storyId","request":{"name":"Dampening Rack","description":"A relay workbench where crews test signal dampening rigs against forged interference."}}"""),
            toolResult(
                c5,
                "createLocationFeature",
                """{"type":"success","data":{"action":"create","entity":"LOCATION_FEATURE","storyId":"$storyId","entityId":"feature-dampening-rack","summary":"Created location feature: Dampening Rack."},"message":"Location feature created."}"""
            ),
            assistant("Done. The relay now has a concrete place for failed and ongoing stabilizer experiments."),
            user("Quick recap before we add the thing that might actually change this."),
            assistant("Recap: failures are escalating, opportunists are exploiting instability, several practical fixes have failed, and the story still lacks a credible anchoring artifact that could shift power and trust."),
            user("Keep the next addition coherent with the trust fracture and map ethics conflict."),
            assistant("Understood. The next foundational artifact should fit both logistics pressure and the cast's moral split over control vs transparency."),
            user("No magic fix vibes. It can help, but it should create tradeoffs."),
            assistant("Noted. I will avoid a perfect solution and preserve costs, bias, or control consequences."),
        )
    }

    private fun user(content: String): Message.User =
        Message.User(content, RequestMetaInfo.create(Clock.System))

    private fun assistant(content: String): Message.Assistant =
        Message.Assistant(content, ResponseMetaInfo.create(Clock.System))

    private fun toolCall(id: String, tool: String, content: String): Message.Tool.Call =
        Message.Tool.Call(id, tool, content, ResponseMetaInfo.create(Clock.System))

    private fun toolResult(id: String, tool: String, content: String): Message.Tool.Result =
        Message.Tool.Result(id, tool, content, RequestMetaInfo.create(Clock.System))
}
