@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.widget.Text

/**
 * A section with a header line.
 */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text("─── $title ───")
        content()
    }
}
