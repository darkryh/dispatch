package com.ead.dispatch.layout

import androidx.compose.runtime.Composable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxSize
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.weight

/**
 * Root contract for one terminal viewport.
 *
 * Header and footer consume their intrinsic height. The body receives all remaining rows and may
 * choose fixed, scrollable, or lazy content independently.
 */
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        header?.invoke()
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            content = content,
        )
        footer?.invoke()
    }
}
