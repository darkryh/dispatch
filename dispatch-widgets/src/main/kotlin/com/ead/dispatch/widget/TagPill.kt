package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Composable
fun TagPill(
    text: String,
    modifier: Modifier = Modifier,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
    paddingHorizontal: Int = 1,
) {
    val contentWidth = (text.length + (paddingHorizontal * 2)).coerceAtLeast(0)
    val appliedModifier = Modifier.width(contentWidth) then modifier
    Background(
        modifier = appliedModifier,
        style =
            BackgroundStyle.Fill(
                fill = fill,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = 0,
            ),
    ) {
        Text(text = text, style = textStyle)
    }
}

@Composable
fun TagList(
    tags: List<String>,
    modifier: Modifier = Modifier,
    gap: Int = 1,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
) {
    if (tags.isEmpty()) return
    Row(modifier = modifier) {
        tags.forEachIndexed { index, tag ->
            TagPill(text = tag, fill = fill, textStyle = textStyle)
            if (index != tags.lastIndex && gap > 0) {
                Spacer(Modifier.width(gap))
            }
        }
    }
}
