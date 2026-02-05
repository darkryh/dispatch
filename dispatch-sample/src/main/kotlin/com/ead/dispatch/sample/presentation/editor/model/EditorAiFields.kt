package com.ead.dispatch.sample.presentation.editor.model

import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object EditorAiFields {
    val fields: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(
            key = EditorFieldKey.PROMPT,
            label = "Prompt",
            placeholder = "Describe what you want the AI to generate",
            maxLines = 4,
            helper = "Be specific about tone, intent, and key requirements.",
            layout = FULL,
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.CONSTRAINTS,
            label = "Constraints",
            placeholder = "Any constraints, limits, or must-haves",
            maxLines = 3,
            layout = FULL,
        ),
    )
}
