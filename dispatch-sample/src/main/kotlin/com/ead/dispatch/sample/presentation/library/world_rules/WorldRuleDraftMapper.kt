package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.sample.domain.agents.world_rule_agent.WorldRuleAIDraft
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

object WorldRuleDraftMapper {
    fun applyDraft(
        draft: WorldRuleAIDraft,
        current: Map<EditorFieldKey, FieldValue>,
    ): Map<EditorFieldKey, FieldValue> {
        val updated = current.toMutableMap()
        set(updated, EditorFieldKey.TITLE, draft.title)
        set(updated, EditorFieldKey.DESCRIPTION, firstNonBlank(draft.rule, draft.summary))
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
