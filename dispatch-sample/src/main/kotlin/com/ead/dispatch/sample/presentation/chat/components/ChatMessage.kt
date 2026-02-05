package com.ead.dispatch.sample.presentation.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.sample.domain.model.message.CliMessage
import com.ead.dispatch.sample.domain.model.message.CliMessageRole
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb


@Dispatchable
fun ChatMessage(
    modifier: Modifier = Modifier,
    message : CliMessage,
) {
    when (message.role) {
        CliMessageRole.USER -> {
            Row(modifier = modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(1))
                Background(
                    modifier = Modifier.weight(1f),
                    style = BackgroundStyle.Fill(
                        fill = rgb("#363C46"),
                    )
                ) {
                    Row {
                        Text(
                            text = "> ",
                            style = rgb("#898D92"),
                        )
                        Text(
                            text = message.data,
                            style = rgb("#FFFFFF"),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(1))
            }
        }
        CliMessageRole.ASSISTANT -> {
            Row(
                modifier = modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "✦ ",
                    style = TextColors.brightCyan
                )
                Text(
                    text = message.data,
                    style = rgb("#FFFFFF"),
                    markdown = true,
                )
                Spacer(modifier = Modifier.width(2))
            }
        }
        CliMessageRole.TOOL -> {
            ToolCallMessage(message = message, modifier = Modifier.fillMaxWidth())
        }
        CliMessageRole.SYSTEM -> {
            // System messages (mode switch notifications, etc.)
            Row(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Spacer(modifier = Modifier.width(2))
                Text(
                    text = "⚙ ",
                    style = rgb("#FFA500") // Orange for system
                )
                Text(
                    text = message.data,
                    style = rgb("#FFA500"),
                )
                Spacer(modifier = Modifier.width(2))
            }
        }
    }
    Spacer(modifier = Modifier.height(1))
}
