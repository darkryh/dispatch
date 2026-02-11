package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Simple filter state holder for list screens.
 */
@Deprecated(
    message = "Unused legacy state holder; use TextFieldState + rememberFilterInputController instead.",
)
class FilterState(initialActive: Boolean = false) {
    var isActive: Boolean by mutableStateOf(initialActive)
}

/**
 * Remember a [FilterState] instance.
 */
@Dispatchable
@Deprecated(
    message = "Unused legacy state holder; use rememberTextFieldState + rememberFilterInputController instead.",
)
fun rememberFilterState(initialActive: Boolean = false): FilterState {
    return remember { FilterState(initialActive) }
}

/**
 * A compact filter input bar for list screens.
 */
@Dispatchable
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
