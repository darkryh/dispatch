package com.ead.dispatch.sample.presentation.library.arcs

import com.ead.dispatch.sample.data.db.type.ArcScope
import com.ead.dispatch.sample.data.db.type.ContentStatus
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout.FULL

object ArcEditorFields {
    private val scopeOptions = ArcScope.entries.map { it.name }
    private val statusOptions = ContentStatus.entries.map { it.name }
    private val scopeHelper = ArcScope.entries.joinToString(", ") { it.name.lowercase().replaceFirstChar { ch -> ch.uppercase() } }
    private val statusHelper = ContentStatus.entries.joinToString(", ") {
        it.name.lowercase().split('_').joinToString(" ") { part ->
            part.replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    val manual: List<EditorFieldDefinition> = listOf(
        EditorFieldDefinition(key = EditorFieldKey.TITLE, label = "Title"),
        EditorFieldDefinition(
            key = EditorFieldKey.SCOPE_TYPE,
            label = "Scope Type",
            placeholder = "Press Enter to cycle",
            helper = scopeHelper,
            options = scopeOptions,
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.SCOPE_ID,
            label = "Scope Id",
            placeholder = "Required when scope is not STORY",
        ),
        EditorFieldDefinition(
            key = EditorFieldKey.STATUS,
            label = "Status",
            placeholder = "Press Enter to cycle",
            helper = statusHelper,
            options = statusOptions,
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
