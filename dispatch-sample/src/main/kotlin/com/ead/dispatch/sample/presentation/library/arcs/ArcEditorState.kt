package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

data class ArcEditorState(
    val storyId: String? = null,
    val arcId: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val values: Map<EditorFieldKey, FieldValue> = emptyMap(),
    val status: String? = null,
    val confirmDelete: Boolean = false,
    val createdAt: Long? = null,
)
