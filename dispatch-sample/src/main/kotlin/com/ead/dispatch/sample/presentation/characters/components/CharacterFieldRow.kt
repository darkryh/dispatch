package com.ead.dispatch.sample.presentation.characters.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.Text

@Dispatchable
fun CharacterFieldRow(
    label: String,
    value: FieldValue,
    maxLines: Int? = null,
    placeholder: String = "Enter value",
    helper: String? = null,
    onValueChange: (String) -> Unit,
    styles: CharacterScreenStyles,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            Text(text = label, style = styles.labelStyle)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            InputTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value.text,
                onValueChange = onValueChange,
                placeholder = placeholder,
                maxLines = maxLines,
                textStyle = styles.fieldText,
                placeholderStyle = styles.fieldPlaceholder,
            )
        }
        if (helper != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = helper,
                    style = styles.fieldPlaceholder,
                )
            }
        }
        Spacer(Modifier.height(1))
    }
}
