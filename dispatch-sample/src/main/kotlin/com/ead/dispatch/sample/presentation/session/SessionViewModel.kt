package com.ead.dispatch.sample.presentation.session

import com.ead.dispatch.sample.domain.SessionManager
import com.ead.dispatch.sample.domain.model.session.Session
import com.ead.dispatch.viewmodel.ViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class SessionViewModel(
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val sessions: StateFlow<List<Session>> = sessionManager.sessionsFlow
        .onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    fun selectSession(session: Session) {
        // Set as current session in SessionManager for ChatViewModel to use
        sessionManager.setCurrentSession(session.id)
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            sessionManager.deleteSession(session.id)
        }
    }

    fun formatRelativeTime(instant: Instant): String {
        val now = Clock.System.now()
        val duration = now - instant

        return when {
            duration.inWholeMinutes < 1 -> "just now"
            duration.inWholeMinutes < 60 -> "${duration.inWholeMinutes} min ago"
            duration.inWholeHours < 24 -> "${duration.inWholeHours} hours ago"
            duration.inWholeDays < 7 -> "${duration.inWholeDays} days ago"
            duration.inWholeDays < 30 -> "${duration.inWholeDays / 7} weeks ago"
            else -> "${duration.inWholeDays / 30} months ago"
        }
    }
}
