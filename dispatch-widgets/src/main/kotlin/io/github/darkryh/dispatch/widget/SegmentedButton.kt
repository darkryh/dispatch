package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.runtime.rememberCallback
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A focusable button that cycles through a list of options on activation (Enter/Return/Space).
 *
 * Focus traversal and keyboard activation are provided by the shared [focusableAction] primitive,
 * so this widget stays in lock-step with [Button] and friends.
 */
@Composable
fun SegmentedButton(
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

    val action =
        focusableAction(modifier, enabled) {
            val next = nextOption(latestValue, options)
            if (next != null) {
                onValueChangeCallback(next)
            }
        }
    val isFocused = action.isFocused
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

    Surface(
        modifier = action.modifier,
        style =
            SurfaceStyle.fill(
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
