package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.Composer
import com.ead.dispatch.runtime.composableWidget

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
@Dispatchable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Outlined,
) = composableWidget("Button") {
    ButtonMeasurable(
        text = text,
        enabled = enabled,
        style = style,
        modifier = modifier,
    )
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
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val (leftCap, rightCap) = when (style) {
            ButtonStyle.Outlined -> "[ " to " ]"
            ButtonStyle.Angled -> "<" to ">"
            ButtonStyle.Text -> "" to ""
            ButtonStyle.Filled -> "▌" to "▐"
            ButtonStyle.Rounded -> "(" to ")"
        }

        val display = "$leftCap$text$rightCap"
        val width = modifiedConstraints.constrainWidth(display.length)

        // In a real implementation, disabled buttons would be dimmed
        val finalDisplay = if (!enabled) {
            display // Would be styled differently
        } else {
            display
        }

        return SimplePlaceable(
            width = width,
            height = 1,
            lines = listOf(finalDisplay.take(width).padEnd(width)),
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
@Dispatchable
fun IconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
) {
    val display = if (bordered) "[$icon]" else icon
    composableWidget("IconButton") {
        object : Measurable {
            override val modifier: Modifier = modifier

            override fun measure(constraints: Constraints): Placeable {
                return SimplePlaceable(
                    width = display.length,
                    height = 1,
                    lines = listOf(display),
                )
            }
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
@Dispatchable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Int = 2,
    content: @Dispatchable () -> Unit,
) {
    val composer = Composer.current
    val node = composer.startNode("ButtonRow")

    content()

    val buttonMeasurables = node.children

    val rowMeasurable = ButtonRowMeasurable(
        modifier = modifier,
        buttons = buttonMeasurables,
        spacing = spacing,
    )

    composer.registerMeasurable(rowMeasurable)
    composer.endNode()
}

internal class ButtonRowMeasurable(
    override val modifier: Modifier,
    private val buttons: List<Measurable>,
    private val spacing: Int,
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val buttonConstraints = Constraints(
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
@Dispatchable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    style: ToggleStyle = ToggleStyle.Checkbox,
) = composableWidget("ToggleButton") {
    ToggleButtonMeasurable(
        checked = checked,
        label = label,
        enabled = enabled,
        style = style,
        modifier = modifier,
    )
}

/**
 * Toggle button styles.
 */
enum class ToggleStyle(val checkedChar: String, val uncheckedChar: String) {
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
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val indicator = if (checked) style.checkedChar else style.uncheckedChar
        val display = if (label != null) "$indicator $label" else indicator

        return SimplePlaceable(
            width = display.length,
            height = 1,
            lines = listOf(display),
        )
    }
}

/**
 * A radio button for single selection.
 */
@Dispatchable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    val indicator = if (selected) "(●)" else "( )"
    val display = if (label != null) "$indicator $label" else indicator
    composableWidget("RadioButton") {
        object : Measurable {
            override val modifier: Modifier = modifier

            override fun measure(constraints: Constraints): Placeable {
                return SimplePlaceable(
                    width = display.length,
                    height = 1,
                    lines = listOf(display),
                )
            }
        }
    }
}
