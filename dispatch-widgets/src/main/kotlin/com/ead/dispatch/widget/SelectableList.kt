package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextStyle
import com.ead.dispatch.widget.Text

data class SelectableListStyles(
    val prefix: TextStyle,
    val selectedPrefix: TextStyle,
    val prefixText: String = "  ",
    val selectedPrefixText: String = "> ",
)

@Dispatchable
fun <T> SelectableList(
    items: List<T>,
    selectedIndex: Int,
    styles: SelectableListStyles,
    modifier: Modifier = Modifier,
    itemSpacing: Int = 0,
    itemContent: @Dispatchable (item: T, isSelected: Boolean) -> Unit,
) {
    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            Row {
                Text(
                    text = if (isSelected) styles.selectedPrefixText else styles.prefixText,
                    style = if (isSelected) styles.selectedPrefix else styles.prefix,
                )
                Spacer(Modifier.width(1))
                Column {
                    itemContent(item, isSelected)
                }
            }
            if (itemSpacing > 0 && index != items.lastIndex) {
                Spacer(Modifier.height(itemSpacing))
            }
        }
    }
}
