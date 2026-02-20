package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.sample.domain.agents.internal.relationship_agent.RelationshipAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object RelationshipDraftMapper {
    fun applyDraft(
        draft: RelationshipAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.SUBJECT_TYPE, draft.subjectType)
        set(updated, EditorFieldKey.OBJECT_TYPE, draft.objectType)
        set(updated, EditorFieldKey.RELATION, draft.relation)

        val notes = buildList {
            addIfNotBlank(draft.summary)
            addIfNotBlank(draft.history?.let { "History: $it" })
            addIfNotBlank(draft.tension?.let { "Tension: $it" })
            addIfNotBlank(draft.currentStatus?.let { "Status: $it" })
            addIfNotBlank(draft.subjectName?.let { "Subject: $it" })
            addIfNotBlank(draft.objectName?.let { "Object: $it" })
        }.joinToString(" · ")

        set(updated, EditorFieldKey.NOTES, notes)
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

    private fun MutableList<String>.addIfNotBlank(value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isNotBlank()) {
            add(trimmed)
        }
    }
}
