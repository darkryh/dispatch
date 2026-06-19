package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A compact filter input bar for list screens.
 */
@Composable
fun FilterBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCursor: Boolean = true,
    icon: String = "Filter: ",
    placeholder: String = "Type to filter...",
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
    maxLines: Int = 1,
    indent: Int = 2,
) {
    Row {
        if (indent > 0) {
            Spacer(Modifier.width(indent))
        }
        TextField(
            value = state.value,
            onValueChange = { state.value = it },
            modifier = modifier,
            icon = icon,
            placeholder = placeholder,
            enabled = enabled,
            singleLine = true,
            showCursor = showCursor && enabled,
            maxLines = maxLines,
            cursorPosition = state.cursorPosition,
            textStyle = textStyle,
            placeholderStyle = placeholderStyle,
            iconStyle = iconStyle,
        )
    }
}
