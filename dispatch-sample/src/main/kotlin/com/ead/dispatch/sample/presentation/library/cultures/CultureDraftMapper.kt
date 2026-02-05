package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.sample.domain.agents.culture_agent.CultureAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object CultureDraftMapper {
    fun applyDraft(
        draft: CultureAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.NAME, draft.name)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.description, draft.summary))
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
