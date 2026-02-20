package com.ead.dispatch.sample.presentation.library.locations

import com.ead.dispatch.sample.domain.agents.internal.location_agent.LocationAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object LocationDraftMapper {
    fun applyDraft(
        draft: LocationAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.NAME, draft.name)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.description, draft.summary))
        setList(updated, EditorFieldKey.TAGS, draft.tags)
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

    private fun setList(
        updated: MutableMap<EditorFieldKey, FieldValue>,
        key: EditorFieldKey,
        values: List<String>,
    ) {
        val filtered = values.map { it.trim() }.filter { it.isNotBlank() }
        if (filtered.isNotEmpty()) {
            updated[key] = FieldValue(filtered.joinToString(", "))
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }
    }
}
