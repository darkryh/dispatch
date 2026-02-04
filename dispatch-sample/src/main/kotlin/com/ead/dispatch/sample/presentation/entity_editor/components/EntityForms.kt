package com.ead.dispatch.sample.presentation.entity_editor.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityFieldDefinition
import com.ead.dispatch.sample.presentation.entity_editor.util.EntityFieldKey
import com.ead.dispatch.sample.presentation.util.FieldValue

@Dispatchable
fun ManualEntityForm(
    fields: List<EntityFieldDefinition>,
    values: Map<EntityFieldKey, FieldValue>,
    styles: EntityScreenStyles,
    onValueChange: (EntityFieldKey, String) -> Unit,
) {
    val splitIndex = (fields.size + 1) / 2
    val leftFields = fields.take(splitIndex)
    val rightFields = fields.drop(splitIndex)

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            leftFields.forEach { field ->
                val value = values[field.key] ?: FieldValue()
                EntityFieldRow(
                    label = field.label,
                    value = value,
                    maxLines = field.maxLines,
                    placeholder = field.placeholder,
                    helper = field.helper,
                    onValueChange = { onValueChange(field.key, it) },
                    styles = styles.copy(labelStyle = styles.labelStylePrimary),
                )
            }
        }
        Spacer(Modifier.width(4))
        Column(modifier = Modifier.weight(1f)) {
            rightFields.forEach { field ->
                val value = values[field.key] ?: FieldValue()
                EntityFieldRow(
                    label = field.label,
                    value = value,
                    maxLines = field.maxLines,
                    placeholder = field.placeholder,
                    helper = field.helper,
                    onValueChange = { onValueChange(field.key, it) },
                    styles = styles.copy(labelStyle = styles.labelStylePrimary),
                )
            }
        }
    }
}

@Dispatchable
fun AutomaticEntityForm(
    fields: List<EntityFieldDefinition>,
    values: Map<EntityFieldKey, FieldValue>,
    styles: EntityScreenStyles,
    onValueChange: (EntityFieldKey, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        fields.forEach { field ->
            val value = values[field.key] ?: FieldValue()
            EntityFieldRow(
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
