package com.ead.dispatch.sample.presentation.library.world_rules

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object WorldRuleEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.TITLE, label = "Title"),
        EditorFieldDefinition(
            key = EditorFieldKey.DESCRIPTION,
            label = "Description",
            placeholder = "Short description",
            maxLines = 3,
            layout = FULL,
        ),
    )
}
