@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.designsystem

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.widget.Panel

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
