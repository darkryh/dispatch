package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Checklist style options.
 */
enum class ChecklistStyle(
    val checkedChar: String,
    val uncheckedChar: String,
) {
    Checkbox("[✓]", "[ ]"),
    Square("[■]", "[ ]"),
    Circle("(●)", "( )"),
    Emoji("✅", "⬜"),
}

/**
 * A checklist entry.
 *
 * Required properties for the Checklist widget:
 * - [name]: label text shown to the user
 * - [checked]: whether the item is completed
 * - [enabled]: whether the item is interactive/active (rendered dim when false)
 */
data class ChecklistItem(
    val name: String,
    val checked: Boolean = false,
    val enabled: Boolean = true,
)

data class ChecklistTextStyles(
    val prefix: TextStyle? = rgb("#ffffff"),
    val selectedPrefix: TextStyle? = rgb("#ffffff"),
    val checkedIndicator: TextStyle? = rgb("#ffffff"),
    val uncheckedIndicator: TextStyle? = rgb("#ffffff"),
    val disabledIndicator: TextStyle? = rgb("#6F7279"),
    val text: TextStyle? = rgb("#ffffff"),
    val selectedText: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
    val checkedText: TextStyle? = rgb("#ffffff"),
    val disabledText: TextStyle? = rgb("#6F7279"),
)

/**
 * An AI-friendly checklist widget.
 *
 * Example:
 * ```kotlin
 * val items = listOf(
 *     ChecklistItem("Review requirements", checked = true),
 *     ChecklistItem("Design solution"),
 * )
 *
 * Checklist(items = items)
 * ```
 */
@Composable
fun Checklist(
    items: List<ChecklistItem>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    style: ChecklistStyle = ChecklistStyle.Checkbox,
    textStyles: ChecklistTextStyles = ChecklistTextStyles(),
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, itemText ->
            val isSelected = index == selectedIndex
            val isEnabled = itemText.enabled
            val isChecked = itemText.checked
            val indicator = if (isChecked) style.checkedChar else style.uncheckedChar
            val prefix = if (isSelected) "> " else "  "

            val prefixStyle = if (isSelected) textStyles.selectedPrefix ?: textStyles.prefix else textStyles.prefix

            val indicatorStyle =
                when {
                    !isEnabled -> textStyles.disabledIndicator
                    isChecked -> textStyles.checkedIndicator
                    else -> textStyles.uncheckedIndicator
                }

            val nameStyle =
                when {
                    !isEnabled -> textStyles.disabledText
                    isChecked -> textStyles.checkedText
                    isSelected -> textStyles.selectedText
                    else -> textStyles.text
                }

            Row {
                Text(prefix, style = prefixStyle)
                Text("$indicator ", style = indicatorStyle)
                Text(itemText.name, style = nameStyle)
            }
        }
    }
}
