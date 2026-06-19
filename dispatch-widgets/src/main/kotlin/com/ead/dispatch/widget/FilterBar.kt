package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Simple filter state holder for list screens.
 */
@Deprecated(
    message = "Unused legacy state holder; use TextFieldState + rememberFilterInputController instead.",
)
class FilterState(
    initialActive: Boolean = false,
) {
    var isActive: Boolean by mutableStateOf(initialActive)
}

/**
 * Remember a [FilterState] instance.
 */
@Composable
@Deprecated(
    message = "Unused legacy state holder; use rememberTextFieldState + rememberFilterInputController instead.",
)
fun rememberFilterState(initialActive: Boolean = false): FilterState = remember { FilterState(initialActive) }

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
