package com.ead.dispatch.sample.domain

import com.ead.dispatch.sample.data.db.SessionRecord
import com.ead.dispatch.sample.data.db.StoryRecord
import com.ead.dispatch.sample.data.db.StructuredIndexRepository
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.dispatch.sample.domain.model.story.WriterMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID

class SessionManager(
    private val repository: StructuredIndexRepository,
) {

    companion object {
        private const val DEFAULT_TITLE = "New Conversation"
        private const val DEFAULT_STORY_TITLE = "New Story"
        private val DEFAULT_MODE = WriterMode.CHAT.name
    }

    // Internal state for reactive updates
    private val _sessionsFlow = MutableStateFlow<List<Session>>(emptyList())
    val sessionsFlow: StateFlow<List<Session>> = _sessionsFlow.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)

    init {
        // Load existing sessions on initialization
        _sessionsFlow.value = loadSessionsFromDatabase()
    }

    /**
     * Load all sessions from database storage
     */
    private fun loadSessionsFromDatabase(): List<Session> =
        repository.getSessions().map { record ->
            Session(
                id = record.id,
                title = record.title,
                updatedAt = Instant.fromEpochMilliseconds(record.updatedAt),
                messageCount = record.messageCount.toInt(),
            )
        }

    private fun persistSession(session: Session, timestamp: Instant) {
        val epochMillis = timestamp.toEpochMilliseconds()
        repository.upsertSession(
            SessionRecord(
                id = session.id,
                title = session.title,
                mode = DEFAULT_MODE,
                createdAt = epochMillis,
                updatedAt = epochMillis,
                messageCount = session.messageCount.toLong(),
                metadataJson = null,
            )
        )
    }

    /**
     * Create a new session
     * @param title Initial title for the session (can be first message preview)
     * @return The created Session
     */
    fun createSession(title: String = DEFAULT_TITLE): Session {
        val now = Clock.System.now()
        val session = Session(
            id = UUID.randomUUID().toString(),
            title = title.take(100), // Limit title length
            updatedAt = now,
            messageCount = 0
        )

        val currentSessions = _sessionsFlow.value.toMutableList()
        currentSessions.add(0, session) // Add to beginning (most recent)
        _sessionsFlow.value = currentSessions
        persistSession(session, now)
        ensureStoryForSession(session, now)

        setCurrentSession(session.id)
        return session
    }

    /**
     * Ensure a session exists with the provided ID.
     *
     * This is useful when restoring a session from persisted checkpoints.
     */
    fun ensureSession(sessionId: String, title: String? = null): Session {
        val existing = getSession(sessionId)
        if (existing != null) {
            ensureStoryForSession(existing, Clock.System.now())
            setCurrentSession(sessionId)
            return existing
        }

        val now = Clock.System.now()
        val session = Session(
            id = sessionId,
            title = (title ?: DEFAULT_TITLE).take(100),
            updatedAt = now,
            messageCount = 0
        )

        val currentSessions = _sessionsFlow.value.toMutableList()
        currentSessions.add(0, session)
        _sessionsFlow.value = currentSessions
        persistSession(session, now)
        ensureStoryForSession(session, now)

        setCurrentSession(sessionId)
        return session
    }

    /**
     * Update an existing session
     */
    fun updateSession(
        sessionId: String,
        title: String? = null,
        incrementMessageCount: Boolean = false
    ) {
        val currentSessions = _sessionsFlow.value.toMutableList()

        val index = currentSessions.indexOfFirst { it.id == sessionId }

        if (index != -1) {
            val existing = currentSessions[index]
            val now = Clock.System.now()

            val updated = existing.copy(
                title = title ?: existing.title,
                updatedAt = now,
                messageCount = if (incrementMessageCount) existing.messageCount + 1 else existing.messageCount
            )

            currentSessions.removeAt(index)
            currentSessions.add(0, updated)

            _sessionsFlow.value = currentSessions
            persistSession(updated, now)
        }
    }

    /**
     * Delete a session
     */
    fun deleteSession(sessionId: String) {
        repository.deleteSession(sessionId)
        val currentSessions = _sessionsFlow.value.filter { it.id != sessionId }
        _sessionsFlow.value = currentSessions

        // Clear current session if deleted
        if (_currentSessionId.value == sessionId) {
            setCurrentSession(null)
        }
    }

    /**
     * Get a specific session by ID
     */
    fun getSession(sessionId: String): Session? {
        return _sessionsFlow.value.find { it.id == sessionId }
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
    fun clearAllSessions() {
        repository.getSessions().forEach { session ->
            repository.deleteSession(session.id)
        }
        _sessionsFlow.value = emptyList()
        _currentSessionId.value = null
    }

    private fun ensureStoryForSession(session: Session, timestamp: Instant) {
        val existing = repository.getStoriesBySession(session.id).firstOrNull()
        if (existing != null) {
            return
        }

        val title = session.title.ifBlank { DEFAULT_STORY_TITLE }
        val epochMillis = timestamp.toEpochMilliseconds()
        repository.upsertStory(
            StoryRecord(
                id = session.id,
                sessionId = session.id,
                title = title,
                genre = null,
                setting = null,
                plotOutline = null,
                status = "draft",
                createdAt = epochMillis,
                updatedAt = epochMillis,
            )
        )
    }
}
