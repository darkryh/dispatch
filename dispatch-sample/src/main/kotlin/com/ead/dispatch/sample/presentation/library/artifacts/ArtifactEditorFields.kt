package com.ead.dispatch.sample.presentation.library.artifacts

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object ArtifactEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.NAME, label = "Name"),
        EditorFieldDefinition(
            key = EditorFieldKey.OWNER_TYPE,
            label = "Owner Type",
            placeholder = "character, organization, etc",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.OWNER_ID,
            label = "Owner",
            placeholder = "Owner id or name",
        ),
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
