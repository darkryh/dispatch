package com.ead.dispatch.sample.presentation.editor.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.presentation.util.FieldValue
import com.ead.dispatch.widget.CycleButton
import com.ead.dispatch.widget.InputTextField
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

@Dispatchable
fun EditorFieldRow(
    label: String,
    value: FieldValue,
    maxLines: Int? = null,
    placeholder: String = "Enter value",
    helper: String? = null,
    options: List<String>? = null,
    onValueChange: (String) -> Unit,
    styles: EditorScreenStyles,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            Text(text = label, style = styles.labelStyle)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(2))
            if (options.isNullOrEmpty()) {
                InputTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = value.text,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    maxLines = maxLines,
                    textStyle = styles.fieldText,
                    placeholderStyle = styles.fieldPlaceholder,
                )
            } else {
                CycleButton(
                    modifier = Modifier.fillMaxWidth(),
                    value = value.text,
                    options = options,
                    onValueChange = onValueChange,
                    placeholder = placeholder,
                    unfocusedFill = rgb("#2E3138"),
                    focusedFill = rgb("#3E6B83"),
                    unfocusedTextStyle = styles.fieldText,
                    focusedTextStyle = rgb("#FFFFFF"),
                    placeholderStyle = styles.fieldPlaceholder,
                    paddingHorizontal = 2,
                    paddingVertical = 1,
                )
            }
        }
        if (helper != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(text = helper, style = styles.fieldPlaceholder)
            }
        }
        Spacer(Modifier.height(1))
    }
}
