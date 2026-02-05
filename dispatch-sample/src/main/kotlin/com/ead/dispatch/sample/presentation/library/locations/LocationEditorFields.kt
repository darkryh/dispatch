package com.ead.dispatch.sample.presentation.library.locations

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object LocationEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.NAME, label = "Name"),
        EditorFieldDefinition(
            key = EditorFieldKey.DESCRIPTION,
            label = "Description",
            placeholder = "Short description",
            maxLines = 3,
            layout = FULL,
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.TAGS,
            label = "Tags",
            placeholder = "comma, separated, tags",
            helper = "Comma-separated list",
            layout = FULL,
        ),
    )
}
