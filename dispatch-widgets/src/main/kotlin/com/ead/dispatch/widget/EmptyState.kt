package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
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
