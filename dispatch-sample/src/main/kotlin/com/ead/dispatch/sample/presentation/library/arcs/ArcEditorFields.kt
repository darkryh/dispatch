package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object ArcEditorFields {
    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.TITLE, label = "Title"),
        EditorFieldDefinition(
            key = EditorFieldKey.SCOPE_TYPE,
            label = "Scope Type",
            placeholder = "STORY, VOLUME, CHAPTER, SCENE, CHARACTER",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.SCOPE_ID,
            label = "Scope Id",
            placeholder = "Required when scope is not STORY",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.STATUS,
            label = "Status",
            placeholder = "DRAFT, IN_PROGRESS, FINAL, ARCHIVED",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.SUMMARY,
            label = "Summary",
            placeholder = "Short summary",
            maxLines = 3,
            layout = FULL,
        ),
    )
}
