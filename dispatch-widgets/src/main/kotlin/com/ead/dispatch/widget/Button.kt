package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.modifier.focusable
import com.ead.dispatch.runtime.LocalFocusRegistry
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.runtime.composableContainer
import com.ead.dispatch.runtime.composableWidget
import com.ead.dispatch.runtime.rememberCallback
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A clickable button.
 *
 * Example:
 * ```kotlin
 * Button(
 *     text = "Submit",
 *     onClick = { submitForm() },
 * )
 * ```
 *
 * @param text Button label.
 * @param onClick Click handler.
 * @param modifier Modifiers to apply.
 * @param enabled Whether the button is enabled.
 * @param style Button visual style.
 */
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Outlined,
) {
    val focus = focusableAction(modifier, enabled, onClick)
    val theme = LocalTheme.current
    composableWidget("Button") {
        ButtonMeasurable(
            text = text,
            enabled = enabled,
            style = style,
            modifier = focus.modifier,
            isFocused = focus.isFocused,
            focusedStyle = theme.selection,
            disabledStyle = theme.muted,
        )
    }
}

/**
 * Button visual styles.
 */
enum class ButtonStyle {
    /**
     * Button with border: [ OK ]
     */
    Outlined,

    /**
     * Button with brackets: <OK>
     */
    Angled,

    /**
     * Minimal button: OK
     */
    Text,

    /**
     * Filled button: ▌OK▐
     */
    Filled,

    /**
     * Rounded button: (OK)
     */
    Rounded,
}

internal class ButtonMeasurable(
    private val text: String,
    private val enabled: Boolean,
    private val style: ButtonStyle,
    override val modifier: Modifier,
    private val isFocused: Boolean = false,
    private val focusedStyle: TextStyle? = null,
    private val disabledStyle: TextStyle? = null,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val (leftCap, rightCap) =
            when (style) {
                ButtonStyle.Outlined -> "[ " to " ]"
                ButtonStyle.Angled -> "<" to ">"
                ButtonStyle.Text -> "" to ""
                ButtonStyle.Filled -> "▌" to "▐"
                ButtonStyle.Rounded -> "(" to ")"
            }

        val display = "$leftCap$text$rightCap"
        val width = modifiedConstraints.constrainWidth(display.length)

        // In a real implementation, disabled buttons would be dimmed
        val visibleDisplay = display.take(width).padEnd(width)
        val finalDisplay = styleActionDisplay(visibleDisplay, enabled, isFocused, focusedStyle, disabledStyle)

        return SimplePlaceable(
            width = width,
            height = 1,
            lines = listOf(finalDisplay),
        )
    }
}

/**
 * An icon button with a single character/emoji.
 *
 * Example:
 * ```kotlin
 * IconButton(icon = "✓", onClick = { confirm() })
 * IconButton(icon = "✕", onClick = { cancel() })
 * ```
 */
@Composable
fun IconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
) {
    val focus = focusableAction(modifier, enabled, onClick)
    val theme = LocalTheme.current
    val display = if (bordered) "[$icon]" else icon
    composableWidget("IconButton") {
        object : Measurable {
            override val modifier: Modifier = focus.modifier

            override fun measure(constraints: Constraints): Placeable =
                SimplePlaceable(
                    width = display.length,
                    height = 1,
                    lines =
                        listOf(
                            styleActionDisplay(
                                display = display,
                                enabled = enabled,
                                isFocused = focus.isFocused,
                                focusedStyle = theme.selection,
                                disabledStyle = theme.muted,
                            ),
                        ),
                )
        }
    }
}

/**
 * A row of buttons.
 *
 * Example:
 * ```kotlin
 * ButtonRow {
 *     Button("Cancel", onClick = { cancel() })
 *     Button("OK", onClick = { submit() })
 * }
 * ```
 */
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Int = 2,
    content: @Composable () -> Unit,
) {
    composableContainer(
        name = "ButtonRow",
        modifier = modifier,
        measurableFactory = { children -> ButtonRowMeasurable(modifier, children, spacing) },
        content = content,
    )
}

internal class ButtonRowMeasurable(
    override val modifier: Modifier,
    private val buttons: List<Measurable>,
    private val spacing: Int,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val buttonConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = modifiedConstraints.maxWidth,
                minHeight = 0,
                maxHeight = 1,
            )

        val placeables = buttons.map { it.measure(buttonConstraints) }

        val totalWidth = placeables.sumOf { it.width } + (spacing * (placeables.size - 1).coerceAtLeast(0))
        val spacer = " ".repeat(spacing)

        val line = placeables.mapNotNull { it.lines.firstOrNull() }.joinToString(spacer)

        return SimplePlaceable(
            width = totalWidth,
            height = 1,
            lines = listOf(line),
        )
    }
}

/**
 * A toggle button / checkbox.
 *
 * Example:
 * ```kotlin
 * var checked by remember { mutableStateOf(false) }
 * ToggleButton(
 *     checked = checked,
 *     onCheckedChange = { checked = it },
 *     label = "Enable feature",
 * )
 * ```
 */
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    style: ToggleStyle = ToggleStyle.Checkbox,
) {
    val focus = focusableAction(modifier, enabled) { onCheckedChange(!checked) }
    val theme = LocalTheme.current
    composableWidget("ToggleButton") {
        ToggleButtonMeasurable(
            checked = checked,
            label = label,
            enabled = enabled,
            style = style,
            modifier = focus.modifier,
            isFocused = focus.isFocused,
            focusedStyle = theme.selection,
            disabledStyle = theme.muted,
        )
    }
}

/**
 * Toggle button styles.
 */
enum class ToggleStyle(
    val checkedChar: String,
    val uncheckedChar: String,
) {
    Checkbox("[✓]", "[ ]"),
    Square("[■]", "[ ]"),
    Circle("(●)", "( )"),
    Switch("[ON ]", "[OFF]"),
    Emoji("✅", "⬜"),
}

internal class ToggleButtonMeasurable(
    private val checked: Boolean,
    private val label: String?,
    private val enabled: Boolean,
    private val style: ToggleStyle,
    override val modifier: Modifier,
    private val isFocused: Boolean = false,
    private val focusedStyle: TextStyle? = null,
    private val disabledStyle: TextStyle? = null,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val indicator = if (checked) style.checkedChar else style.uncheckedChar
        val display = if (label != null) "$indicator $label" else indicator

        return SimplePlaceable(
            width = display.length,
            height = 1,
            lines = listOf(styleActionDisplay(display, enabled, isFocused, focusedStyle, disabledStyle)),
        )
    }
}

/**
 * A radio button for single selection.
 */
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val focus = focusableAction(modifier, enabled, onClick)
    val theme = LocalTheme.current
    val indicator = if (selected) "(●)" else "( )"
    val display = if (label != null) "$indicator $label" else indicator
    composableWidget("RadioButton") {
        object : Measurable {
            override val modifier: Modifier = focus.modifier

            override fun measure(constraints: Constraints): Placeable =
                SimplePlaceable(
                    width = display.length,
                    height = 1,
                    lines =
                        listOf(
                            styleActionDisplay(
                                display = display,
                                enabled = enabled,
                                isFocused = focus.isFocused,
                                focusedStyle = theme.selection,
                                disabledStyle = theme.muted,
                            ),
                        ),
                )
        }
    }
}

@Composable
private fun focusableAction(
    modifier: Modifier,
    enabled: Boolean,
    onActivate: () -> Unit,
): ActionFocus {
    val focusRegistry = LocalFocusRegistry.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val focusToken = remember { Any() }
    val activate = rememberCallback(onActivate)

    DisposableEffect(enabled, focusRegistry, keyboardInterceptor) {
        if (!enabled) return@DisposableEffect onDispose {}
        val dispose =
            keyboardInterceptor.register(priority = -1) { event ->
                if (!focusRegistry.isFocused(focusToken) || !focusRegistry.claimEvent(event)) {
                    return@register false
                }
                when {
                    event.key == "Tab" && !event.shift && !event.ctrl && !event.alt -> {
                        focusRegistry.focusNext()
                        true
                    }
                    event.shift && event.key.equals("q", ignoreCase = true) -> {
                        focusRegistry.focusPrevious()
                        true
                    }
                    event.key == "Enter" || event.key == "Return" || event.key == "Space" || event.key == " " -> {
                        activate()
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }

    return ActionFocus(
        modifier = if (enabled) modifier.focusable(focusToken) else modifier,
        isFocused = enabled && focusRegistry.isFocused(focusToken),
    )
}

private data class ActionFocus(
    val modifier: Modifier,
    val isFocused: Boolean,
)

private fun styleActionDisplay(
    display: String,
    enabled: Boolean,
    isFocused: Boolean,
    focusedStyle: TextStyle?,
    disabledStyle: TextStyle?,
): String =
    when {
        !enabled && disabledStyle != null -> disabledStyle(display)
        isFocused && focusedStyle != null -> focusedStyle(display)
        else -> display
    }
