@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    leftPadding: Int = 2,
    titleStyle: TextStyle = rgb("#A7B2BF"),
    descriptionStyle: TextStyle = rgb("#6E7681"),
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (leftPadding > 0) {
                Spacer(Modifier.width(leftPadding))
            }
            Text(text = title, style = titleStyle)
        }
        if (!description.isNullOrBlank()) {
            Spacer(Modifier.height(1))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (leftPadding > 0) {
                    Spacer(Modifier.width(leftPadding))
                }
                Text(text = description, style = descriptionStyle)
            }
        }
    }
}
