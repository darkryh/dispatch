@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ead.dispatch.input.Key
import com.ead.dispatch.input.asKeyEvent
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.widget.Text

/** One selectable row in a [SelectMenu]. */
class MenuItem(
    val label: String,
    val hint: String = "",
    val onActivate: () -> Unit,
)

private class MutableRef<T>(var value: T)

/**
 * An arrow-navigable menu: ↑/↓ move the highlight, Enter activates the row. No Tab required.
 *
 * Intended for menus/lists (no text inputs nearby), so the arrow keys don't collide with widgets
 * that use them internally.
 */
@Composable
fun SelectMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    priority: Int = 50,
) {
    val theme = LocalTheme.current
    val interceptor = LocalKeyboardInterceptor.current
    var selected by remember { mutableStateOf(0) }
    val count = items.size
    if (count in 1..selected) selected = count - 1

    // The interceptor closure outlives a single composition; read the latest list through a ref.
    val itemsRef = remember { MutableRef(items) }.also { it.value = items }

    DisposableEffect(enabled, count == 0, interceptor) {
        if (!enabled || count == 0) return@DisposableEffect onDispose {}
        val dispose =
            interceptor.register(priority = priority) { rawEvent ->
                val list = itemsRef.value
                val size = list.size
                if (size == 0) {
                    return@register false
                }
                val event = rawEvent.asKeyEvent()
                when (event.key) {
                    Key.ArrowUp -> {
                        selected = (selected - 1 + size) % size
                        true
                    }
                    Key.ArrowDown -> {
                        selected = (selected + 1) % size
                        true
                    }
                    Key.Enter -> {
                        list.getOrNull(selected)?.onActivate?.invoke()
                        true
                    }
                    else -> false
                }
            }
        onDispose { dispose() }
    }

    Column(modifier = modifier) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            Row {
                Text(if (isSelected) "❯ " else "  ", style = if (isSelected) theme.accent else theme.muted)
                Text(item.label, style = if (isSelected) theme.accent else theme.primary)
                if (item.hint.isNotEmpty()) {
                    Text("  —  ${item.hint}", style = theme.muted)
                }
            }
        }
    }
}
