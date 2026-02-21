package com.ead.dispatch.sample.presentation.chat_mode.chat.components

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
fun ChatProgressAnimation(
    isProcessing: Boolean,
    processingElapsedSeconds: Long,
) {
    val animation = remember { mutableStateOf(0) }

    LaunchedEffect(isProcessing) {
        if (!isProcessing) {
            animation.value = 0
            return@LaunchedEffect
        }

        while (isProcessing) {
            delay(100)
            if (animation.value < SpinnerStyle.Dots.frames.size - 1) {
                animation.value = animation.value.inc()
            } else {
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

                Spacer(Modifier.width(1))

                Text(
                    text = "(${formatProcessingDuration(processingElapsedSeconds)} • esc to interrupt)",
                    style = rgb("#82858A"),
                )
            }

            Spacer(Modifier.height(1))
        }
    }
}

internal fun formatProcessingDuration(elapsedSeconds: Long): String {
    val normalized = elapsedSeconds.coerceAtLeast(0L)
    val hours = normalized / 3_600L
    val minutes = (normalized % 3_600L) / 60L
    val seconds = normalized % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes.toTwoDigits()}m ${seconds.toTwoDigits()}s"
        minutes > 0L -> "${minutes}m ${seconds.toTwoDigits()}s"
        else -> "${seconds}s"
    }
}

private fun Long.toTwoDigits(): String = if (this < 10L) "0$this" else toString()
