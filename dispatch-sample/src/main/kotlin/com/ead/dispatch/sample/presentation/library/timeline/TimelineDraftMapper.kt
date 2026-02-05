package com.ead.dispatch.sample.presentation.library.timeline

import com.ead.dispatch.sample.domain.agents.timeline_agent.TimelineAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object TimelineDraftMapper {
    fun applyDraft(
        draft: TimelineAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.TITLE, draft.title)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.description, draft.summary))
        draft.orderIndex?.let { updated[EditorFieldKey.ORDER_INDEX] = FieldValue(it.toString()) }
        return updated
    }

    private fun set(
        updated: MutableMap<EditorFieldKey, FieldValue>,
        key: EditorFieldKey,
        value: String?,
    ) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isNotBlank()) {
            updated[key] = FieldValue(trimmed)
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }
}
