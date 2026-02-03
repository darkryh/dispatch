package com.ead.dispatch.sample.presentation.option.model

import com.ead.dispatch.sample.domain.entity.EntityOptionType

data class EntityOptionState(
    val isLoading: Boolean = true,
    val storyId: String? = null,
    val type: EntityOptionType? = null,
    val items: List<EntityPreview> = emptyList(),
    val error: String? = null,
)
