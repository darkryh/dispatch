package com.ead.dispatch.sample.presentation.chat_mode.chat.components

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.CommandOption
import com.ead.dispatch.widget.CommandPalette
import com.ead.dispatch.widget.CommandPaletteState
import com.ead.dispatch.widget.CommandPaletteTextStyles
import com.ead.dispatch.widget.rememberCommandPaletteState

@Dispatchable
fun <T> ChatCommandPalette(
    options: List<CommandOption<T>>,
    inputValue: String,
    onOptionSelected: (CommandOption<T>) -> Unit,
    onInputTransform: (String) -> Unit,
    modifier: Modifier = Modifier,
    triggerChar: Char = '/',
    commandPrefix: String? = triggerChar.toString(),
    selectionIndicator: String? = null,
    visibleCount: Int = 6,
    textStyles: CommandPaletteTextStyles = CommandPaletteTextStyles(),
    showDescriptions: Boolean = true,
    showIcons: Boolean = false,
    noResultsText: String = "No matching commands",
    enabled: Boolean = true,
    state: CommandPaletteState<T> = rememberCommandPaletteState(),
) {
    Row {
        Spacer(modifier = Modifier.width(2))

        CommandPalette(
            options = options,
            inputValue = inputValue,
            onOptionSelected = onOptionSelected,
            onInputTransform = onInputTransform,
            modifier = modifier,
            triggerChar = triggerChar,
            commandPrefix = commandPrefix,
            selectionIndicator = selectionIndicator,
            visibleCount = visibleCount,
            textStyles = textStyles,
            showDescriptions = showDescriptions,
            showIcons = showIcons,
            noResultsText = noResultsText,
            enabled = enabled,
            state = state,
        )
    }

}