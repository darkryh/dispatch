package com.ead.dispatch.sample.data.repositories

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ead.dispatch.sample.DispatchDatabase
import com.ead.dispatch.sample.data.db.entities.*
import com.ead.dispatch.sample.data.db.type.*
import com.ead.dispatch.sample.domain.model.story.OverflowList
import com.ead.dispatch.sample.domain.model.story.StoryChatContext
import com.ead.dispatch.sample.domain.model.story.StoryModeContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

class StructuredIndexRepository(
    private val database: DispatchDatabase,
) {
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
        const val CHAT_CONTEXT_CHARACTER_LIMIT = 8
        const val CHAT_CONTEXT_LOCATION_LIMIT = 8
        const val CHAT_CONTEXT_ARC_LIMIT = 6
        const val CHAT_CONTEXT_WORLD_RULE_LIMIT = 4
        const val CHAT_CONTEXT_CULTURE_LIMIT = 4
        const val CHAT_CONTEXT_EVENT_LIMIT = 4
        const val CHAT_CONTEXT_ORGANIZATION_LIMIT = 4
        const val CHAT_CONTEXT_RELATIONSHIP_LIMIT = 4
        const val CHAT_CONTEXT_LOCATION_FEATURE_LIMIT = 4
        const val CHAT_CONTEXT_ARTIFACT_LIMIT = 4
        const val CHAT_CONTEXT_TIMELINE_LIMIT = 4
    }

    private val queries = database.dispatchDatabaseQueries
    private val coroutineDispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "dispatch-db")
    }.asCoroutineDispatcher()

    private suspend inline fun <T> dbQuery(crossinline block: () -> T): T =
        withContext(coroutineDispatcher) { block() }

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

    fun observeSessions(): Flow<List<SessionRecord>> =
        queries.selectSessions { id, title, mode, createdAt, updatedAt, messageCount ->
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
        }
            .asFlow()
            .mapToList(coroutineDispatcher)
            .map { sessions ->
                sessions.map { session ->
                    session.copy(metadata = getSessionMetadata(session.id))
                }
            }
            .flowOn(coroutineDispatcher)

    suspend fun upsertSession(record: SessionRecord) = dbQuery {
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

    suspend fun getSessions(): List<SessionRecord> = dbQuery {
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

    suspend fun deleteSession(sessionId: String) = dbQuery {
        queries.deleteSession(sessionId)
    }

    suspend fun upsertStory(record: StoryRecord) = dbQuery {
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

    suspend fun getStoriesBySession(sessionId: String): List<StoryRecord> = dbQuery {
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

    suspend fun getStoryById(storyId: String): StoryRecord? = dbQuery {
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

    fun observeStoriesBySession(sessionId: String): Flow<List<StoryRecord>> =
        queries.selectStoriesBySessionId(sessionId) { id, sessionId, title, genre, setting, plotOutline, logline, theme, tone, stakes, pov, tense, targetAudience, pacing, status, createdAt, updatedAt ->
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
        }
            .asFlow()
            .mapToList(coroutineDispatcher)
            .map { stories ->
                stories.map { story ->
                    story.copy(
                        styleRefs = getStoryStyleRefs(story.id),
                        emotionalBeats = getStoryEmotionalBeats(story.id),
                    )
                }
            }
            .flowOn(coroutineDispatcher)

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
        val worldRuleSlice = limitLatest(getWorldRulesByStory(storyId), CHAT_CONTEXT_WORLD_RULE_LIMIT) { it.createdAt }
        val cultureSlice = limitLatest(getCulturesByStory(storyId), CHAT_CONTEXT_CULTURE_LIMIT) { it.createdAt }
        val eventSlice = limitLatest(getEventsByStory(storyId), CHAT_CONTEXT_EVENT_LIMIT) { it.createdAt }
        val organizationSlice = limitLatest(getOrganizationsByStory(storyId), CHAT_CONTEXT_ORGANIZATION_LIMIT) { it.createdAt }
        val relationshipSlice = limitLatest(getRelationshipsByStory(storyId), CHAT_CONTEXT_RELATIONSHIP_LIMIT) { it.createdAt }
        val locationFeatureSlice = limitLatest(getLocationFeaturesByStory(storyId), CHAT_CONTEXT_LOCATION_FEATURE_LIMIT) { it.createdAt }
        val artifactSlice = limitLatest(getArtifactsByStory(storyId), CHAT_CONTEXT_ARTIFACT_LIMIT) { it.createdAt }
        val timelineSlice = limitLatest(getTimelineEntriesByStory(storyId), CHAT_CONTEXT_TIMELINE_LIMIT) { it.createdAt }

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

    suspend fun replaceStoryCharacters(storyId: String, characters: List<StoryCharacterRecord>) = dbQuery {
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

    suspend fun insertStoryCharacter(record: StoryCharacterRecord) = dbQuery {
        database.transaction {
            insertStoryCharacterInternal(record.storyId, record)
            replaceStoryCharacterDetails(record)
        }
    }

    suspend fun updateStoryCharacter(record: StoryCharacterRecord) = dbQuery {
        database.transaction {
            val physical = record.physical
            queries.updateStoryCharacter(
                id = record.id,
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

    suspend fun deleteStoryCharacter(characterId: String) = dbQuery {
        database.transaction {
            queries.deleteStoryCharacterTraitsByCharacterId(characterId)
            queries.deleteStoryCharacterRolesByCharacterId(characterId)
            queries.deleteStoryCharacterQuirksByCharacterId(characterId)
            queries.deleteStoryCharacterById(characterId)
        }
    }

    suspend fun getStoryCharacters(storyId: String): List<StoryCharacterRecord> = dbQuery {
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

    suspend fun upsertVolume(record: StoryVolumeRecord) = dbQuery {
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

    suspend fun getVolumesByStory(storyId: String): List<StoryVolumeRecord> = dbQuery {
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

    suspend fun upsertChapter(record: StoryChapterRecord) = dbQuery {
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

    suspend fun getChaptersByVolume(volumeId: String): List<StoryChapterRecord> = dbQuery {
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

    suspend fun upsertLocation(record: StoryLocationRecord) = dbQuery {
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

    suspend fun deleteStoryLocation(locationId: String) = dbQuery {
        database.transaction {
            queries.deleteStoryLocationTagsByLocationId(locationId)
            queries.deleteStoryLocationById(locationId)
        }
    }

    suspend fun getLocationsByStory(storyId: String): List<StoryLocationRecord> = dbQuery {
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

    suspend fun replaceLocationsByStory(storyId: String, locations: List<StoryLocationRecord>) = dbQuery {
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

    suspend fun upsertArc(record: StoryArcRecord) = dbQuery {
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

    suspend fun deleteStoryArc(arcId: String) = dbQuery {
        queries.deleteStoryArcById(arcId)
    }

    suspend fun getArcsByStory(storyId: String): List<StoryArcRecord> = dbQuery {
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

    suspend fun getArcsByScope(scopeType: ArcScope, scopeId: String): List<StoryArcRecord> = dbQuery {
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

    suspend fun replaceArcsByStory(storyId: String, arcs: List<StoryArcRecord>) = dbQuery {
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

    suspend fun replaceArcsByScope(scopeType: ArcScope, scopeId: String, arcs: List<StoryArcRecord>) = dbQuery {
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

    suspend fun upsertScene(record: StorySceneRecord) = dbQuery {
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

    suspend fun getScenesByChapter(chapterId: String): List<StorySceneRecord> = dbQuery {
        queries.selectScenesByChapterId(chapterId) { id, chapterId, number, title, summary, contentRange, pov, emotionalBeat, locationId, timeSpan, status, createdAt, updatedAt ->
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
                keyEvents = getSceneKeyEvents(id),
                status = status?.let(ContentStatus.Companion::fromDb),
                createdAt = createdAt,
                updatedAt = updatedAt,
            )
        }.executeAsList()
    }

    suspend fun replaceScenesByChapter(chapterId: String, scenes: List<StorySceneRecord>) = dbQuery {
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

    suspend fun insertStoryWorldRule(record: StoryWorldRuleRecord) = dbQuery {
        queries.insertStoryWorldRule(
            id = record.id,
            story_id = record.storyId,
            title = record.title,
            description = record.description,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryWorldRule(record: StoryWorldRuleRecord) = dbQuery {
        queries.updateStoryWorldRule(
            id = record.id,
            title = record.title,
            description = record.description,
        )
    }

    suspend fun deleteStoryWorldRule(ruleId: String) = dbQuery {
        queries.deleteStoryWorldRuleById(ruleId)
    }

    suspend fun getWorldRulesByStory(storyId: String): List<StoryWorldRuleRecord> = dbQuery {
        queries.selectWorldRulesByStoryId(storyId) { id, storyId, title, description, createdAt ->
            StoryWorldRuleRecord(
                id = id,
                storyId = storyId,
                title = title,
                description = description,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryCulture(record: StoryCultureRecord) = dbQuery {
        queries.insertStoryCulture(
            id = record.id,
            story_id = record.storyId,
            name = record.name,
            description = record.description,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryCulture(record: StoryCultureRecord) = dbQuery {
        queries.updateStoryCulture(
            id = record.id,
            name = record.name,
            description = record.description,
        )
    }

    suspend fun deleteStoryCulture(cultureId: String) = dbQuery {
        queries.deleteStoryCultureById(cultureId)
    }

    suspend fun getCulturesByStory(storyId: String): List<StoryCultureRecord> = dbQuery {
        queries.selectCulturesByStoryId(storyId) { id, storyId, name, description, createdAt ->
            StoryCultureRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryEvent(record: StoryEventRecord) = dbQuery {
        queries.insertStoryEvent(
            id = record.id,
            story_id = record.storyId,
            name = record.name,
            description = record.description,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryEvent(record: StoryEventRecord) = dbQuery {
        queries.updateStoryEvent(
            id = record.id,
            name = record.name,
            description = record.description,
        )
    }

    suspend fun deleteStoryEvent(eventId: String) = dbQuery {
        queries.deleteStoryEventById(eventId)
    }

    suspend fun getEventsByStory(storyId: String): List<StoryEventRecord> = dbQuery {
        queries.selectEventsByStoryId(storyId) { id, storyId, name, description, createdAt ->
            StoryEventRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryOrganization(record: StoryOrganizationRecord) = dbQuery {
        queries.insertStoryOrganization(
            id = record.id,
            story_id = record.storyId,
            name = record.name,
            description = record.description,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryOrganization(record: StoryOrganizationRecord) = dbQuery {
        queries.updateStoryOrganization(
            id = record.id,
            name = record.name,
            description = record.description,
        )
    }

    suspend fun deleteStoryOrganization(organizationId: String) = dbQuery {
        queries.deleteStoryOrganizationById(organizationId)
    }

    suspend fun getOrganizationsByStory(storyId: String): List<StoryOrganizationRecord> = dbQuery {
        queries.selectOrganizationsByStoryId(storyId) { id, storyId, name, description, createdAt ->
            StoryOrganizationRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryRelationship(record: StoryRelationshipRecord) = dbQuery {
        queries.insertStoryRelationship(
            id = record.id,
            story_id = record.storyId,
            subject_id = record.subjectId,
            subject_type = record.subjectType,
            object_id = record.objectId,
            object_type = record.objectType,
            relation = record.relation,
            notes = record.notes,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryRelationship(record: StoryRelationshipRecord) = dbQuery {
        queries.updateStoryRelationship(
            id = record.id,
            subject_id = record.subjectId,
            subject_type = record.subjectType,
            object_id = record.objectId,
            object_type = record.objectType,
            relation = record.relation,
            notes = record.notes,
        )
    }

    suspend fun deleteStoryRelationship(relationshipId: String) = dbQuery {
        queries.deleteStoryRelationshipById(relationshipId)
    }

    suspend fun getRelationshipsByStory(storyId: String): List<StoryRelationshipRecord> = dbQuery {
        queries.selectRelationshipsByStoryId(storyId) { id, storyId, subjectId, subjectType, objectId, objectType, relation, notes, createdAt ->
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
            )
        }.executeAsList()
    }

    suspend fun insertStoryLocationFeature(record: StoryLocationFeatureRecord) = dbQuery {
        queries.insertStoryLocationFeature(
            id = record.id,
            story_id = record.storyId,
            location_id = record.locationId,
            name = record.name,
            description = record.description,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryLocationFeature(record: StoryLocationFeatureRecord) = dbQuery {
        queries.updateStoryLocationFeature(
            id = record.id,
            location_id = record.locationId,
            name = record.name,
            description = record.description,
        )
    }

    suspend fun deleteStoryLocationFeature(featureId: String) = dbQuery {
        queries.deleteStoryLocationFeatureById(featureId)
    }

    suspend fun getLocationFeaturesByStory(storyId: String): List<StoryLocationFeatureRecord> = dbQuery {
        queries.selectLocationFeaturesByStoryId(storyId) { id, storyId, locationId, name, description, createdAt ->
            StoryLocationFeatureRecord(
                id = id,
                storyId = storyId,
                locationId = locationId,
                name = name,
                description = description,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryArtifact(record: StoryArtifactRecord) = dbQuery {
        queries.insertStoryArtifact(
            id = record.id,
            story_id = record.storyId,
            name = record.name,
            description = record.description,
            owner_id = record.ownerId,
            owner_type = record.ownerType,
            location_id = record.locationId,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryArtifact(record: StoryArtifactRecord) = dbQuery {
        queries.updateStoryArtifact(
            id = record.id,
            name = record.name,
            description = record.description,
            owner_id = record.ownerId,
            owner_type = record.ownerType,
            location_id = record.locationId,
        )
    }

    suspend fun deleteStoryArtifact(artifactId: String) = dbQuery {
        queries.deleteStoryArtifactById(artifactId)
    }

    suspend fun getArtifactsByStory(storyId: String): List<StoryArtifactRecord> = dbQuery {
        queries.selectArtifactsByStoryId(storyId) { id, storyId, name, description, ownerId, ownerType, locationId, createdAt ->
            StoryArtifactRecord(
                id = id,
                storyId = storyId,
                name = name,
                description = description,
                ownerId = ownerId,
                ownerType = ownerType,
                locationId = locationId,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertStoryTimelineEntry(record: StoryTimelineEntryRecord) = dbQuery {
        queries.insertStoryTimelineEntry(
            id = record.id,
            story_id = record.storyId,
            title = record.title,
            description = record.description,
            order_index = record.orderIndex,
            created_at = record.createdAt,
        )
    }

    suspend fun updateStoryTimelineEntry(record: StoryTimelineEntryRecord) = dbQuery {
        queries.updateStoryTimelineEntry(
            id = record.id,
            title = record.title,
            description = record.description,
            order_index = record.orderIndex,
        )
    }

    suspend fun deleteStoryTimelineEntry(entryId: String) = dbQuery {
        queries.deleteStoryTimelineEntryById(entryId)
    }

    suspend fun getTimelineEntriesByStory(storyId: String): List<StoryTimelineEntryRecord> = dbQuery {
        queries.selectTimelineEntriesByStoryId(storyId) { id, storyId, title, description, orderIndex, createdAt ->
            StoryTimelineEntryRecord(
                id = id,
                storyId = storyId,
                title = title,
                description = description,
                orderIndex = orderIndex,
                createdAt = createdAt,
            )
        }.executeAsList()
    }

    suspend fun insertRagDocument(record: RagDocumentRecord) = dbQuery {
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

    suspend fun getRagDocumentsByChapter(chapterId: String): List<RagDocumentRecord> = dbQuery {
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

    suspend fun deleteRagDocument(docId: String) = dbQuery {
        queries.deleteRagDocumentById(docId)
    }
}
