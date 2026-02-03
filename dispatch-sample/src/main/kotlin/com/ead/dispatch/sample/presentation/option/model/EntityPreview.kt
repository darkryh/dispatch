package com.ead.dispatch.sample.presentation.option.model

data class EntityPreview(
    val id: String,
    val title: String,
    val subtitle: String,
    val isCreate: Boolean = false,
)
