package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.weight
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.LocalDispatchConfig
import com.ead.dispatch.runtime.LocalExitPromptState
import com.ead.dispatch.sample.domain.model.story.WriterMode
import com.ead.dispatch.ui.Modifier
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

/**
 * Status bar component showing current mode, state, and hints.
 *
 * @param writerMode The current Writer Assistant mode (CHAT or CHAT_STORY)
 */
@Dispatchable
fun ChatStatusBar(
    isCommandPaletteVisible: Boolean,
    writerMode: WriterMode,
    contextRemainingPercent: Int?,
) {
    val exitPromptState = LocalExitPromptState.current
    val dispatchConfig = LocalDispatchConfig.current

    // Mode indicator colors
    val modeColor = when (writerMode) {
        WriterMode.CHAT -> rgb("#289389")
        WriterMode.CHAT_STORY -> rgb("#A3D9E5")
    }

    val modeText = when (writerMode) {
        WriterMode.CHAT -> "⏸ chat mode"
        WriterMode.CHAT_STORY -> "⏸ story mode"
    }


    if (exitPromptState.isArmed) {
        val exitLabel = dispatchConfig.exitKeyBindings
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" or ") { it.label() }
            ?: "Ctrl+C"
        Text(
            text = "  Press $exitLabel again to exit"
        )
    }
    else {
        if (!isCommandPaletteVisible) {
            Row {
                // Mode indicator
                Text(
                    text = "  $modeText ",
                    style = modeColor,
                )

                // Status text
                Text(
                    text = "(shift+tab to cycle)"
                )

                contextRemainingPercent?.let { remaining ->
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "$remaining% context left",
                        style = rgb("#A6B6A7")
                    )
                    Spacer(modifier = Modifier.width(2))
                }
            }
        }
    }

}
