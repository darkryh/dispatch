package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.data.db.type.SessionMode
import com.ead.dispatch.sample.data.db.entities.SessionRecord
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.sample.data.repositories.StructuredIndexRepository
import com.ead.dispatch.sample.domain.model.session.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class SessionManager(
    private val repository: StructuredIndexRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    companion object {
        private const val DEFAULT_TITLE = "New Conversation"
        private const val DEFAULT_STORY_TITLE = StoryRecord.PLACEHOLDER_TITLE
        private val DEFAULT_MODE = SessionMode.CHAT
    }

    private val _sessionsFlow = MutableStateFlow<List<Session>>(emptyList())
    val sessionsFlow: StateFlow<List<Session>> = _sessionsFlow.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            cleanupEmptySessions()
            refreshSessions()
            createDraftSession()
        }
    }

    private suspend fun refreshSessions() {
        _sessionsFlow.value = repository.getSessions().map { record -> record.toSession() }
    }

    private fun SessionRecord.toSession(): Session =
        Session(
            id = id,
            title = profile.title,
            updatedAt = Instant.fromEpochMilliseconds(updatedAt),
            messageCount = stats.messageCount.toInt(),
        )

    private suspend fun persistSession(
        session: Session,
        createdAtMillis: Long,
        updatedAtMillis: Long,
    ) {
        repository.upsertSession(
            SessionRecord(
                id = session.id,
                profile = SessionRecord.SessionProfile(
                    title = session.title,
                    mode = DEFAULT_MODE,
                ),
                createdAt = createdAtMillis,
                updatedAt = updatedAtMillis,
                stats = SessionRecord.SessionStats(
                    messageCount = session.messageCount.toLong(),
                ),
                metadata = emptyMap(),
            )
        )
        refreshSessions()
    }

    /**
     * Create a new session
     * @param title Initial title for the session (can be first message preview)
     * @return The created Session
     */
    suspend fun createSession(title: String = DEFAULT_TITLE): Session {
        val now = Clock.System.now()
        val epochMillis = now.toEpochMilliseconds()
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = title.take(100), // Limit title length
            updatedAt = now,
            messageCount = 0
        )

        persistSession(
            session = session,
            createdAtMillis = epochMillis,
            updatedAtMillis = epochMillis,
        )
        ensureStoryForSession(session, now)

        setCurrentSession(session.id)
        return session
    }

    /**
     * Ensure a session exists with the provided ID.
     *
     * This is useful when restoring a session from persisted checkpoints.
     */
    suspend fun ensureSession(sessionId: String, title: String? = null): Session {
        val existingRecord = repository.getSessionById(sessionId)
        if (existingRecord != null) {
            val existing = existingRecord.toSession()
            ensureStoryForSession(existing, Clock.System.now())
            setCurrentSession(sessionId)
            refreshSessions()
            return existing
        }

        val now = Clock.System.now()
        val epochMillis = now.toEpochMilliseconds()
        val session = Session(
            id = sessionId,
            title = (title ?: DEFAULT_TITLE).take(100),
            updatedAt = now,
            messageCount = 0
        )

        persistSession(
            session = session,
            createdAtMillis = epochMillis,
            updatedAtMillis = epochMillis,
        )
        ensureStoryForSession(session, now)

        setCurrentSession(sessionId)
        return session
    }

    /**
     * Update an existing session
     */
    suspend fun updateSession(
        sessionId: String,
        title: String? = null,
        incrementMessageCount: Boolean = false
    ) {
        val existingRecord = repository.getSessionById(sessionId) ?: return
        val existing = existingRecord.toSession()
        val now = Clock.System.now()
        val nowMillis = now.toEpochMilliseconds()

        val updated = existing.copy(
            title = title ?: existing.title,
            updatedAt = now,
            messageCount = if (incrementMessageCount) existing.messageCount + 1 else existing.messageCount
        )

        persistSession(
            session = updated,
            createdAtMillis = existingRecord.createdAt,
            updatedAtMillis = nowMillis,
        )
    }

    /**
     * Delete a session
     */
    suspend fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
        refreshSessions()

        // Clear current session if deleted
        if (_currentSessionId.value == sessionId) {
            setCurrentSession(null)
        }
    }

    /**
     * Get a specific session by ID
     */
    fun getSession(sessionId: String): Session? {
        return sessionsFlow.value.find { it.id == sessionId }
    }

    fun getCurrentSession(): Session? {
        val currentId = _currentSessionId.value ?: return null
        return getSession(currentId)
    }

    /**
     * Set the current active session
     */
    fun setCurrentSession(sessionId: String?) {
        _currentSessionId.value = sessionId
    }

    /**
     * Clear all sessions (for testing/reset)
     */
    suspend fun clearAllSessions() {
        repository.getSessions().forEach { session ->
            repository.deleteSession(session.id)
        }
        refreshSessions()
        _currentSessionId.value = null
    }

    private suspend fun cleanupEmptySessions() {
        val sessions = repository.getSessions()
        sessions
            .filter { it.stats.messageCount <= 0L }
            .forEach { session ->
                repository.deleteSession(session.id)
            }
    }

    private suspend fun createDraftSession() {
        if (_currentSessionId.value != null) return
        if (repository.getSessions().isNotEmpty()) return
        createSession(DEFAULT_TITLE)
    }

    private suspend fun ensureStoryForSession(session: Session, timestamp: Instant) {
        val existing = repository.getStoriesBySession(session.id).firstOrNull()
        if (existing != null) {
            return
        }

        val title = DEFAULT_STORY_TITLE
        val epochMillis = timestamp.toEpochMilliseconds()

        repository.upsertStory(
            StoryRecord(
                id = session.id,
                sessionId = session.id,
                title = title,
                genre = null,
                setting = null,
                plotOutline = null,
                status = ContentStatus.DRAFT,
                createdAt = epochMillis,
                updatedAt = epochMillis,
            )
        )
    }
}
