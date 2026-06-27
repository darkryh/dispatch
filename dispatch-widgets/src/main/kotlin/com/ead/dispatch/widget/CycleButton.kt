package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.rememberCallback
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A focusable button that cycles through a list of options on Enter.
 */
@Composable
fun CycleButton(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Press Enter",
    unfocusedFill: TextStyle = rgb("#2E3138"),
    focusedFill: TextStyle = rgb("#6BE3FF"),
    unfocusedTextStyle: TextStyle = rgb("#FFFFFF"),
    focusedTextStyle: TextStyle = rgb("#0B0F14"),
    placeholderStyle: TextStyle = rgb("#82858A"),
    paddingHorizontal: Int = 2,
    paddingVertical: Int = 1,
) {
    val onValueChangeCallback = rememberCallback(onValueChange)
    var latestValue by remember { mutableStateOf(value) }
    latestValue = value
    val focusRegistry = LocalFocusRegistry.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val focusToken = remember { Any() }
    val focusableModifier = if (enabled) modifier.focusable(focusToken) else modifier

    val isFocused = enabled && focusRegistry.isFocused(focusToken)
    val fillStyle = if (isFocused) focusedFill else unfocusedFill
    val displayValue =
        if (value.isBlank()) {
            placeholder
        } else {
            formatOptionLabel(value)
        }
    val textStyle =
        when {
            value.isBlank() -> placeholderStyle
            isFocused -> focusedTextStyle
            else -> unfocusedTextStyle
        }

    DisposableEffect(listOf(enabled, focusRegistry, keyboardInterceptor, options)) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val dispose =
            keyboardInterceptor.register(priority = -1) { event ->
                if (!focusRegistry.isFocused(focusToken)) {
                    return@register false
                }
                if (!focusRegistry.claimEvent(event)) {
                    return@register false
                }

                if (event.key == "Tab" && !event.ctrl && !event.alt) {
                    if (event.shift) focusRegistry.focusPrevious() else focusRegistry.focusNext()
                    return@register true
                }

                if (event.key == "ArrowDown" || event.key == "Down") {
                    focusRegistry.focusNext()
                    return@register true
                }

                if (event.key == "ArrowUp" || event.key == "Up") {
                    focusRegistry.focusPrevious()
                    return@register true
                }

                if (event.shift && (event.key == "Q" || event.key == "q")) {
                    focusRegistry.focusPrevious()
                    return@register true
                }

                if (event.key == "Enter" || event.key == "Return") {
                    val next = nextOption(latestValue, options)
                    if (next != null) {
                        onValueChangeCallback(next)
                    }
                    return@register true
                }

                false
            }

        onDispose {
            dispose()
        }
    }

    Background(
        modifier = focusableModifier,
        style =
            BackgroundStyle.fill(
                fill = fillStyle,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = paddingVertical,
            ),
    ) {
        Text(text = displayValue, style = textStyle)
    }
}

internal fun nextOption(
    current: String,
    options: List<String>,
): String? {
    if (options.isEmpty()) return null
    val currentIndex = options.indexOf(current)
    val nextIndex = if (currentIndex < 0) 0 else (currentIndex + 1) % options.size
    return options[nextIndex]
}

internal fun formatOptionLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    val isUpper = trimmed.uppercase() == trimmed
    if (!isUpper) return trimmed
    val parts = trimmed.split('_').filter { it.isNotBlank() }
    if (parts.isEmpty()) return trimmed
    return parts.joinToString(" ") { part ->
        part.lowercase().replaceFirstChar { ch -> ch.uppercase() }
    }
}
