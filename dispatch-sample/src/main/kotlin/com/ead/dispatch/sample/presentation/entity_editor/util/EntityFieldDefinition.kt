package com.ead.dispatch.sample.presentation.entity_editor.util

data class EntityFieldDefinition(
    val key: EntityFieldKey,
    val label: String,
    val placeholder: String = "Enter value",
    val maxLines: Int? = null,
    val helper: String? = null,
)
