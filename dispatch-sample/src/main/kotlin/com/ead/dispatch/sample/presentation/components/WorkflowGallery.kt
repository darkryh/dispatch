@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.DecisionOption
import com.ead.dispatch.widget.DecisionPrompt
import com.ead.dispatch.widget.TextField
import com.ead.dispatch.sample.widgets.SectionHeader

@Composable
internal fun WorkflowGallery() {
    GalleryScreen("Workflow", "Command discovery and explicit decision prompts") {
        SectionHeader("CommandPalette")
        TextField(
            value = "/he",
            onValueChange = {},
            enabled = false,
            showCursor = false,
            icon = "> ",
        )
        CommandPalette(
            options =
                listOf(
                    CommandOption("help", "Show keyboard help", data = "help"),
                    CommandOption("history", "Open recent commands", data = "history"),
                ),
            inputValue = "/he",
            onOptionSelected = {},
            onInputTransform = {},
            enabled = false,
            selectionIndicator = "> ",
        )
        SectionHeader("DecisionPrompt")
        DecisionPrompt(
            question = "Apply the rendered changes?",
            options = listOf(DecisionOption("Apply"), DecisionOption("Review later")),
            onSubmit = {},
            enabled = false,
            placeholder = "Custom response",
        )
    }
}
