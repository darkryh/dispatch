package com.ead.dispatch.sample.presentation.library

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.FilterBar
import com.ead.dispatch.widget.TextFieldState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun ListFilterBar(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showCursor: Boolean = true,
    placeholder: String = "Type to filter...",
) {
    val theme = LocalTheme.current
    Background(
        modifier = modifier.fillMaxWidth(),
        style = BackgroundStyle.Fill(
            fill = rgb("#363C46"),
            paddingHorizontal = 1,
            paddingVertical = 1,
        ),
    ) {
        FilterBar(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            showCursor = showCursor,
            icon = "⚙ ",
            placeholder = placeholder,
            textStyle = theme.primary,
            placeholderStyle = theme.primary + TextStyle(dim = true),
            indent = 0,
        )
    }
}
