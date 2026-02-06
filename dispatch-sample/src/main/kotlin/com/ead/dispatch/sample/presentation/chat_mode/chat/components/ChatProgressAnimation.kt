package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import ai.koog.prompt.structure.markdown.markdownStreamingParser
import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LaunchedEffect
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.widget.Spinner
import com.ead.dispatch.widget.SpinnerStyle
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import kotlinx.coroutines.delay

/**
 * Status bar component showing current state and hints.
 *
 * @param isProcessing Whether the assistant is processing a message.
 */
@Dispatchable
fun ChatProgressAnimation(isProcessing: Boolean) {
    val animation = remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(100)
            if(animation.value < SpinnerStyle.Dots.frames.size - 1) {
                animation.value = animation.value.inc()
            }
            else {
                animation.value = 0
            }
        }
    }


    if (isProcessing) {
        Column {
            Row {
                Spacer(Modifier.width(2))

                Spinner(frame = animation.value, style = SpinnerStyle.Dots)

                Spacer(Modifier.width(1))

                Text(
                    text = "let him cook",
                    style = rgb("#FFFFFF"),
                )
            }

            Spacer(Modifier.height(1))
        }
    }
}
