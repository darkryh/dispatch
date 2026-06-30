package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
    paddingHorizontal: Int = 1,
) {
    val contentWidth = (text.length + (paddingHorizontal * 2)).coerceAtLeast(0)
    val appliedModifier = Modifier.width(contentWidth) then modifier
    Surface(
        modifier = appliedModifier,
        style =
            SurfaceStyle.fill(
                fill = fill,
                paddingHorizontal = paddingHorizontal,
                paddingVertical = 0,
            ),
    ) {
        Text(text = text, style = textStyle)
    }
}

@Composable
fun ChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    gap: Int = 1,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
) {
    if (tags.isEmpty()) return
    Row(modifier = modifier) {
        tags.forEachIndexed { index, tag ->
            Chip(text = tag, fill = fill, textStyle = textStyle)
            if (index != tags.lastIndex && gap > 0) {
                Spacer(Modifier.width(gap))
            }
        }
    }
}
