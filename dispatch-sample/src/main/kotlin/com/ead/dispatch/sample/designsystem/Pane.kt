@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.widget.Panel

/**
 * A titled, rounded container used for the two panes of a [PlaygroundScaffold] (the live "Preview"
 * and the editable "Controls"). Thin wrapper over [Panel] so every pane looks identical.
 */
@Composable
fun Pane(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Panel(modifier = modifier.fillMaxWidth(), title = title) {
        content()
    }
}
