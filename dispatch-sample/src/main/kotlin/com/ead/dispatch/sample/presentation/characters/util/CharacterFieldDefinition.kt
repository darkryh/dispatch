package com.ead.dispatch.sample.presentation.characters.util

data class CharacterFieldDefinition(
    val key: CharacterFieldKey,
    val label: String,
    val maxLines: Int? = null,
    val placeholder: String = "Enter value",
    val helper: String? = null,
)