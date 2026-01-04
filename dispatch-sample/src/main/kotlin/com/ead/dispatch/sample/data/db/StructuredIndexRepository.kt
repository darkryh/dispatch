package com.ead.dispatch.sample.data.db

import com.ead.dispatch.sample.DispatchDatabase
import com.ead.dispatch.sample.domain.util.JsonLists

class StructuredIndexRepository(
    private val database: DispatchDatabase,
) {
    private val queries = database.dispatchDatabaseQueries

    fun upsertSession(record: SessionRecord) {
        database.transaction {
            val existing = queries.selectSessionById(record.id).executeAsOneOrNull()
            if (existing == null) {
                queries.insertSession(
                    id = record.id,
                    title = record.title,
                    mode = record.mode,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                    message_count = record.messageCount,
                    metadata_json = record.metadataJson,
                )
            } else {
                queries.updateSession(
                    id = record.id,
                    title = record.title,
                    mode = record.mode,
                    updated_at = record.updatedAt,
                    message_count = record.messageCount,
                    metadata_json = record.metadataJson,
                )
            }
        }
    }

    fun getSessions(): List<SessionRecord> =
        queries.selectSessions { id, title, mode, created_at, updated_at, message_count, metadata_json ->
            SessionRecord(
                id = id,
                title = title,
                mode = mode,
                createdAt = created_at,
                updatedAt = updated_at,
                messageCount = message_count,
                metadataJson = metadata_json,
            )
        }.executeAsList()

    fun deleteSession(sessionId: String) {
        queries.deleteSession(sessionId)
    }

    fun upsertStory(record: StoryRecord) {
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
                    logline = record.logline,
                    theme = record.theme,
                    tone = record.tone,
                    stakes = record.stakes,
                    pov = record.pov,
                    tense = record.tense,
                    target_audience = record.targetAudience,
                    pacing = record.pacing,
                    status = record.status,
                    style_refs_json = JsonLists.encode(record.styleRefs),
                    emotional_beats_json = JsonLists.encode(record.emotionalBeats),
                    key_locations_json = JsonLists.encode(record.keyLocations),
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
                    logline = record.logline,
                    theme = record.theme,
                    tone = record.tone,
                    stakes = record.stakes,
                    pov = record.pov,
                    tense = record.tense,
                    target_audience = record.targetAudience,
                    pacing = record.pacing,
                    status = record.status,
                    style_refs_json = JsonLists.encode(record.styleRefs),
                    emotional_beats_json = JsonLists.encode(record.emotionalBeats),
                    key_locations_json = JsonLists.encode(record.keyLocations),
                    updated_at = record.updatedAt,
                )
            }
        }
    }

    fun getStoriesBySession(sessionId: String): List<StoryRecord> =
        queries.selectStoriesBySessionId(sessionId) { id, session_id, title, genre, setting, plot_outline, logline, theme, tone, stakes, pov, tense, target_audience, pacing, status, style_refs_json, emotional_beats_json, key_locations_json, created_at, updated_at ->
            StoryRecord(
                id = id,
                sessionId = session_id,
                title = title,
                genre = genre,
                setting = setting,
                plotOutline = plot_outline,
                logline = logline,
                theme = theme,
                tone = tone,
                stakes = stakes,
                pov = pov,
                tense = tense,
                targetAudience = target_audience,
                pacing = pacing,
                status = status,
                styleRefs = JsonLists.decode(style_refs_json),
                emotionalBeats = JsonLists.decode(emotional_beats_json),
                keyLocations = JsonLists.decode(key_locations_json),
                createdAt = created_at,
                updatedAt = updated_at,
            )
        }.executeAsList()

    fun replaceStoryCharacters(storyId: String, characters: List<StoryCharacterRecord>) {
        database.transaction {
            queries.deleteStoryCharactersByStoryId(storyId)
            characters.forEach { character ->
                queries.insertStoryCharacter(
                    id = character.id,
                    story_id = storyId,
                    name = character.name,
                    description = character.description,
                    traits_json = character.traitsJson,
                    roles_json = JsonLists.encode(character.roles),
                    goal = character.goal,
                    motivation = character.motivation,
                    flaw = character.flaw,
                    arc = character.arc,
                    temperament = character.temperament,
                    age = character.age,
                    pronouns = character.pronouns,
                    occupation = character.occupation,
                    backstory = character.backstory,
                    voice = character.voice,
                    internal_conflict = character.internalConflict,
                    quirks_json = JsonLists.encode(character.quirks),
                    created_at = character.createdAt,
                )
            }
        }
    }

    fun getStoryCharacters(storyId: String): List<StoryCharacterRecord> =
        queries.selectCharactersByStoryId(storyId) { id, story_id, name, description, traits_json, roles_json, goal, motivation, flaw, arc, temperament, age, pronouns, occupation, backstory, voice, internal_conflict, quirks_json, created_at ->
            StoryCharacterRecord(
                id = id,
                storyId = story_id,
                name = name,
                description = description,
                traitsJson = traits_json,
                roles = JsonLists.decode(roles_json),
                goal = goal,
                motivation = motivation,
                flaw = flaw,
                arc = arc,
                temperament = temperament,
                age = age,
                pronouns = pronouns,
                occupation = occupation,
                backstory = backstory,
                voice = voice,
                internalConflict = internal_conflict,
                quirks = JsonLists.decode(quirks_json),
                createdAt = created_at,
            )
        }.executeAsList()

    fun upsertVolume(record: StoryVolumeRecord) {
        database.transaction {
            val existing = queries.selectVolumeById(record.id).executeAsOneOrNull()
            if (existing == null) {
                queries.insertStoryVolume(
                    id = record.id,
                    story_id = record.storyId,
                    number = record.number,
                    title = record.title,
                    summary = record.summary,
                    arc = record.arc,
                    target_word_count = record.targetWordCount,
                    key_events_json = JsonLists.encode(record.keyEvents),
                    status = record.status,
                    notes = record.notes,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                )
            } else {
                queries.updateStoryVolume(
                    id = record.id,
                    title = record.title,
                    summary = record.summary,
                    arc = record.arc,
                    target_word_count = record.targetWordCount,
                    key_events_json = JsonLists.encode(record.keyEvents),
                    status = record.status,
                    notes = record.notes,
                    updated_at = record.updatedAt,
                )
            }
        }
    }

    fun getVolumesByStory(storyId: String): List<StoryVolumeRecord> =
        queries.selectVolumesByStoryId(storyId) { id, story_id, number, title, summary, arc, target_word_count, key_events_json, status, notes, created_at, updated_at ->
            StoryVolumeRecord(
                id = id,
                storyId = story_id,
                number = number,
                title = title,
                summary = summary,
                arc = arc,
                targetWordCount = target_word_count,
                keyEvents = JsonLists.decode(key_events_json),
                status = status,
                notes = notes,
                createdAt = created_at,
                updatedAt = updated_at,
            )
        }.executeAsList()

    fun upsertChapter(record: StoryChapterRecord) {
        database.transaction {
            val existing = queries.selectChapterById(record.id).executeAsOneOrNull()
            if (existing == null) {
                queries.insertStoryChapter(
                    id = record.id,
                    volume_id = record.volumeId,
                    number = record.number,
                    title = record.title,
                    summary = record.summary,
                    content = record.content,
                    content_ref = record.contentRef,
                    word_count = record.wordCount,
                    pov = record.pov,
                    emotional_beat = record.emotionalBeat,
                    key_events_json = JsonLists.encode(record.keyEvents),
                    target_word_count = record.targetWordCount,
                    location = record.location,
                    time_span = record.timeSpan,
                    status = record.status,
                    created_at = record.createdAt,
                    updated_at = record.updatedAt,
                )
            } else {
                queries.updateStoryChapter(
                    id = record.id,
                    title = record.title,
                    summary = record.summary,
                    content = record.content,
                    content_ref = record.contentRef,
                    word_count = record.wordCount,
                    pov = record.pov,
                    emotional_beat = record.emotionalBeat,
                    key_events_json = JsonLists.encode(record.keyEvents),
                    target_word_count = record.targetWordCount,
                    location = record.location,
                    time_span = record.timeSpan,
                    status = record.status,
                    updated_at = record.updatedAt,
                )
            }
        }
    }

    fun getChaptersByVolume(volumeId: String): List<StoryChapterRecord> =
        queries.selectChaptersByVolumeId(volumeId) { id, volume_id, number, title, summary, content, content_ref, word_count, pov, emotional_beat, key_events_json, target_word_count, location, time_span, status, created_at, updated_at ->
            StoryChapterRecord(
                id = id,
                volumeId = volume_id,
                number = number,
                title = title,
                summary = summary,
                content = content,
                contentRef = content_ref,
                wordCount = word_count,
                pov = pov,
                emotionalBeat = emotional_beat,
                keyEvents = JsonLists.decode(key_events_json),
                targetWordCount = target_word_count,
                location = location,
                timeSpan = time_span,
                status = status,
                createdAt = created_at,
                updatedAt = updated_at,
            )
        }.executeAsList()

    fun replaceStoryFacts(storyId: String, facts: List<StoryFactRecord>) {
        database.transaction {
            queries.deleteStoryFactsByStoryId(storyId)
            facts.forEach { fact ->
                queries.insertStoryFact(
                    id = fact.id,
                    story_id = storyId,
                    fact_type = fact.factType.name,
                    content = fact.content,
                    created_at = fact.createdAt,
                )
            }
        }
    }

    fun getFactsByStory(storyId: String): List<StoryFactRecord> =
        queries.selectFactsByStoryId(storyId) { id, story_id, fact_type, content, created_at ->
            StoryFactRecord(
                id = id,
                storyId = story_id,
                factType = StoryFactType.fromDb(fact_type),
                content = content,
                createdAt = created_at,
            )
        }.executeAsList()

    fun insertRagDocument(record: RagDocumentRecord) {
        queries.insertRagDocument(
            doc_id = record.docId,
            session_id = record.sessionId,
            story_id = record.storyId,
            volume_id = record.volumeId,
            chapter_id = record.chapterId,
            chunk_index = record.chunkIndex,
            source_type = record.sourceType,
            source_ref = record.sourceRef,
            checksum = record.checksum,
            created_at = record.createdAt,
        )
    }

    fun getRagDocumentsByChapter(chapterId: String): List<RagDocumentRecord> =
        queries.selectRagDocumentsByChapterId(chapterId) { doc_id, session_id, story_id, volume_id, chapter_id, chunk_index, source_type, source_ref, checksum, created_at ->
            RagDocumentRecord(
                docId = doc_id,
                sessionId = session_id,
                storyId = story_id,
                volumeId = volume_id,
                chapterId = chapter_id,
                chunkIndex = chunk_index,
                sourceType = source_type,
                sourceRef = source_ref,
                checksum = checksum,
                createdAt = created_at,
            )
        }.executeAsList()

    fun deleteRagDocument(docId: String) {
        queries.deleteRagDocumentById(docId)
    }
}
