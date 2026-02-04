package com.ead.dispatch.sample.presentation.entity_editor.state

import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityEditorMode
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

data class EntityEditorState(
    val storyId: String? = null,
    val entityId: String? = null,
    val type: EntityOptionType? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val values: Map<EntityFieldKey, FieldValue> = emptyMap(),
    val mode: EntityEditorMode = EntityEditorMode.MANUAL,
    val status: String? = null,
    val confirmDelete: Boolean = false,
    val createdAt: Long? = null,
)
