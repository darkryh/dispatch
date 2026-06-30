@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leftPadding: Int = 2,
    titleStyle: TextStyle = rgb("#E6EAF0") + TextStyle(bold = true),
    subtitleStyle: TextStyle = rgb("#A7B2BF"),
    subtitleSpacing: Int = 1,
    subtitleOnNewLine: Boolean = false,
) {
    if (!subtitleOnNewLine) {
        Row(modifier = modifier.fillMaxWidth()) {
            if (leftPadding > 0) {
                Spacer(Modifier.width(leftPadding))
            }
            Text(text = title, style = titleStyle)
            if (!subtitle.isNullOrBlank()) {
                if (subtitleSpacing > 0) {
                    Spacer(Modifier.width(subtitleSpacing))
                }
                Text(text = subtitle, style = subtitleStyle)
            }
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (leftPadding > 0) {
                Spacer(Modifier.width(leftPadding))
            }
            Text(text = title, style = titleStyle)
        }
        if (!subtitle.isNullOrBlank()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (leftPadding > 0) {
                    Spacer(Modifier.width(leftPadding))
                }
                Text(text = subtitle, style = subtitleStyle)
            }
        }
    }
}
