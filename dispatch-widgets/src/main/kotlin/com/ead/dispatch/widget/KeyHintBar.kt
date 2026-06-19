package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

data class KeyHint(
    val key: String,
    val description: String,
)

@Composable
fun KeyHintBar(
    hints: List<KeyHint>,
    modifier: Modifier = Modifier,
    leftPadding: Int = 2,
    keyStyle: TextStyle = rgb("#A7B2BF"),
    descriptionStyle: TextStyle = rgb("#8A95A5"),
    separator: String = "·",
    separatorStyle: TextStyle = rgb("#6E7681"),
) {
    if (hints.isEmpty()) return

    Row(modifier = modifier.fillMaxWidth()) {
        if (leftPadding > 0) {
            Spacer(Modifier.width(leftPadding))
        }
        hints.forEachIndexed { index, hint ->
            Text(text = hint.key, style = keyStyle)
            Spacer(Modifier.width(1))
            Text(text = hint.description, style = descriptionStyle)
            if (index != hints.lastIndex) {
                Spacer(Modifier.width(1))
                Text(text = separator, style = separatorStyle)
                Spacer(Modifier.width(1))
            }
        }
    }
}
