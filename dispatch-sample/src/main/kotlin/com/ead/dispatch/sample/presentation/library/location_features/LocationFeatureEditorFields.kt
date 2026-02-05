package com.ead.dispatch.sample.presentation.library.location_features

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object LocationFeatureEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.NAME, label = "Name"),
        EditorFieldDefinition(
            key = EditorFieldKey.LOCATION_ID,
            label = "Location",
            placeholder = "Location id or name",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.DESCRIPTION,
            label = "Description",
            placeholder = "Short description",
            maxLines = 3,
            layout = FULL,
        ),
    )
}
