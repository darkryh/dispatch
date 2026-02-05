package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.width
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
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
