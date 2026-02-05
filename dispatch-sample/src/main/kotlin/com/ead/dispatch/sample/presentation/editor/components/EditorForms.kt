package com.ead.dispatch.sample.presentation.editor.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldDefinition
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldKey
import com.ead.dispatch.sample.presentation.editor.model.EditorFieldLayout
import com.ead.dispatch.sample.presentation.util.FieldValue

@Dispatchable
fun ManualEditorForm(
    fields: List<EditorFieldDefinition>,
    values: Map<EditorFieldKey, FieldValue>,
    styles: EditorScreenStyles,
    onValueChange: (EditorFieldKey, String) -> Unit,
) {
    fun renderField(field: EditorFieldDefinition) {
        val value = values[field.key] ?: FieldValue()
        EditorFieldRow(
            label = field.label,
            value = value,
            maxLines = field.maxLines,
            placeholder = field.placeholder,
            helper = field.helper,
            onValueChange = { onValueChange(field.key, it) },
            styles = styles.copy(labelStyle = styles.labelStylePrimary),
        )
    }

    val rows = mutableListOf<List<EditorFieldDefinition>>()
    var pending: EditorFieldDefinition? = null
    fields.forEach { field ->
        if (field.layout == EditorFieldLayout.FULL) {
            pending?.let {
                rows.add(listOf(it))
                pending = null
            }
            rows.add(listOf(field))
        } else {
            if (pending == null) {
                pending = field
            } else {
                rows.add(listOf(pending, field))
                pending = null
            }
        }
    }
    pending?.let { rows.add(listOf(it)) }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            val isFullWidth = row.size == 1 && row.first().layout == EditorFieldLayout.FULL
            if (isFullWidth) {
                renderField(row.first())
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        renderField(row.first())
                    }
                    Spacer(Modifier.width(4))
                    Column(modifier = Modifier.weight(1f)) {
                        row.getOrNull(1)?.let { renderField(it) }
                    }
                }
            }
        }
    }
}

@Dispatchable
fun AiEditorForm(
    fields: List<EditorFieldDefinition>,
    values: Map<EditorFieldKey, FieldValue>,
    styles: EditorScreenStyles,
    onValueChange: (EditorFieldKey, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        fields.forEach { field ->
            val value = values[field.key] ?: FieldValue()
            EditorFieldRow(
                label = field.label,
                value = value,
                maxLines = field.maxLines,
                placeholder = field.placeholder,
                helper = field.helper,
                onValueChange = { onValueChange(field.key, it) },
                styles = styles.copy(labelStyle = styles.labelStyleSecondary),
            )
        }
    }
}
