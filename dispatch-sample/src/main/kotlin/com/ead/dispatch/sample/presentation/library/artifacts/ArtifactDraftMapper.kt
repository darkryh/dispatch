package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.sample.domain.agents.artifact_agent.ArtifactAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object ArtifactDraftMapper {
    fun applyDraft(
        draft: ArtifactAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.NAME, draft.name)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.description, draft.summary))
        set(updated, EditorFieldKey.OWNER_TYPE, draft.ownerType)
        set(updated, EditorFieldKey.OWNER_ID, draft.owner)
        set(updated, EditorFieldKey.LOCATION_ID, draft.location)
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
