package com.ead.dispatch.sample.presentation.library.relationships

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object RelationshipEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(
            key = EditorFieldKey.SUBJECT_TYPE,
            label = "Subject Type",
            placeholder = "character, organization, etc",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.SUBJECT_ID,
            label = "Subject Id",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.OBJECT_TYPE,
            label = "Object Type",
            placeholder = "character, organization, etc",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.OBJECT_ID,
            label = "Object Id",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.RELATION,
            label = "Relation",
            placeholder = "mentor, rival, sibling",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.NOTES,
            label = "Notes",
            placeholder = "Optional notes",
            maxLines = 3,
            layout = FULL,
        ),
    )
}
