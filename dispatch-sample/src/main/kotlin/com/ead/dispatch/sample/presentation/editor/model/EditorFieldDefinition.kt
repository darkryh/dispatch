package com.ead.dispatch.sample.presentation.editor.model

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.HALF

data class EditorFieldDefinition(
    val key: EditorFieldKey,
    val label: String,
    val placeholder: String = "Enter value",
    val helper: String? = null,
    val maxLines: Int? = null,
    val layout: EditorFieldLayout = HALF,
)
