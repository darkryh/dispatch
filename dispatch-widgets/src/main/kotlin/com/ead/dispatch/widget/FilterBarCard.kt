package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A filter bar wrapped in a filled background surface.
 */
@Composable
fun FilterBarCard(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCursor: Boolean = true,
    placeholder: String = "Type to filter...",
    icon: String = "⚙ ",
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    backgroundFill: TextStyle = rgb("#363C46"),
    paddingHorizontal: Int = 1,
    paddingVertical: Int = 1,
    indent: Int = 0,
) {
    Background(
        modifier = modifier.fillMaxWidth(),
        style =
            BackgroundStyle.Fill(
                fill = backgroundFill,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = paddingVertical,
            ),
    ) {
        FilterBar(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            showCursor = showCursor,
            icon = icon,
            placeholder = placeholder,
            textStyle = textStyle,
            placeholderStyle = placeholderStyle,
            indent = indent,
        )
    }
}
