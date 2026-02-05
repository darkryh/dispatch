package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.sample.domain.agents.location_feature_agent.LocationFeatureAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object LocationFeatureDraftMapper {
    fun applyDraft(
        draft: LocationFeatureAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.NAME, draft.name)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.description, draft.summary))
        set(updated, EditorFieldKey.LOCATION_ID, draft.relatedLocation)
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
