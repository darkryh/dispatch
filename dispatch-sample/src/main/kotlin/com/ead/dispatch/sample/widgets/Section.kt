@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.widget.Text

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
