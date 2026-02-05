package com.ead.dispatch.sample.presentation.library.events

import com.ead.dispatch.sample.domain.agents.event_agent.EventAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorAIMode
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorMode
import com.ead.dispatch.sample.presentation.util.FieldValue

data class EventEditorState(
    val storyId: String? = null,
    val eventId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val values: Map<EditorFieldKey, FieldValue> = emptyMap(),
    val mode: EditorMode = EditorMode.MANUAL,
    val status: String? = null,
    val confirmDelete: Boolean = false,
    val createdAt: Long? = null,
    val isGenerating: Boolean = false,
    val aiDraft: EventAIDraft? = null,
    val aiError: String? = null,
    val aiMode: EditorAIMode = EditorAIMode.CREATIVE,
)
