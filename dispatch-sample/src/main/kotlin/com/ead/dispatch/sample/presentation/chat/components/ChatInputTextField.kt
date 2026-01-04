package com.ead.dispatch.sample.presentation.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.sample.presentation.chat.event.ChatEvent
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.InputTextField
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun ChatInputTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
) {
    Background(
        modifier = Modifier.fillMaxWidth(),
        style = BackgroundStyle.Fill(
            fill = rgb("#363C46"),
        )
    ) {
        InputTextField(
            modifier = modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            icon = icon,
            placeholder = placeholder,
            enabled = enabled,
            onSubmit = onSubmit,
            showCursor = showCursor,
            cursorChar = cursorChar,
            maxLines = maxLines,
            textStyle = textStyle,
            placeholderStyle = placeholderStyle,
            iconStyle = iconStyle,
        )
    }
}