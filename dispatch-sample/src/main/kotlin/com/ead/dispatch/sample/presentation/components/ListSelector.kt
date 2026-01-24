package com.ead.dispatch.sample.presentation.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextStyle

data class ListOption<T>(
    val id: String,
    val title: String,
    val description: String? = null,
    val data: T,
    val enabled: Boolean = true,
)

data class ListSelectorTextStyles(
    val prefix: TextStyle,
    val selectedPrefix: TextStyle,
    val title: TextStyle,
    val selectedTitle: TextStyle,
    val description: TextStyle,
    val selectedDescription: TextStyle,
)

@Dispatchable
fun <T> ListSelector(
    options: List<ListOption<T>>,
    selectedIndex: Int,
    textStyles: ListSelectorTextStyles,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            val prefix = if (isSelected) "> " else "  "
            val prefixStyle = if (isSelected) textStyles.selectedPrefix else textStyles.prefix
            val titleStyle = if (isSelected) textStyles.selectedTitle else textStyles.title
            val descStyle = if (isSelected) textStyles.selectedDescription else textStyles.description

            Row {
                Text(prefix, style = prefixStyle)
                Text(option.title, style = titleStyle)
                if (!option.description.isNullOrBlank()) {
                    Spacer(Modifier.width(2))
                    Text(option.description, style = descStyle)
                }
            }
        }
    }
}
