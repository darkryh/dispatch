package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.rememberCallback

/**
 * Represents a single command option in the palette.
 *
 * @param T The type of data associated with this option.
 * @param label The display label for the command.
 * @param description Optional description shown next to the label.
 * @param icon Optional icon/emoji prefix.
 * @param data The data payload associated with this option.
 * @param enabled Whether this option can be selected.
 */
data class CommandOption<T>(
    val label: String,
    val description: String = "",
    val icon: String? = null,
    val data: T,
    val enabled: Boolean = true,
)

/**
 * Text styles for the command palette.
 *
 * @param prefix Style for the selection prefix of unselected items.
 * @param selectedPrefix Style for the selection prefix of the selected item.
 * @param label Style for the label text of unselected items.
 * @param selectedLabel Style for the label text of the selected item.
 * @param description Style for the description text of unselected items.
 * @param selectedDescription Style for the description text of the selected item.
 * @param disabledLabel Style for disabled item labels.
 * @param noResultsText Style for the "no results" message.
 */
data class CommandPaletteTextStyles(
    val prefix: TextStyle? = rgb("#ffffff"),
    val selectedPrefix: TextStyle? = rgb("#00BFFF") + TextStyle(bold = false),
    val label: TextStyle? = rgb("#ffffff"),
    val selectedLabel: TextStyle? = rgb("#ffffff") + TextStyle(bold = false),
    val description: TextStyle? = rgb("#6F7279"),
    val selectedDescription: TextStyle? = rgb("#898D92"),
    val disabledLabel: TextStyle? = rgb("#6F7279"),
    val noResultsText: TextStyle? = rgb("#898D92"),
)

/**
 * State holder for the command palette.
 */
class CommandPaletteState<T>(
    initialVisible: Boolean = false,
    initialSelectedIndex: Int = 0,
) {
    private val core = FilterableSelectorState<CommandOption<T>>(initialVisible, initialSelectedIndex)

    /**
     * Whether the palette is currently visible.
     */
    var isVisible: Boolean
        get() = core.isVisible
        internal set(value) {
            core.isVisible = value
        }

    /**
     * Currently selected option index within [filteredOptions].
     */
    var selectedIndex: Int
        get() = core.selectedIndex
        internal set(value) {
            core.selectedIndex = value
        }

    /**
     * Current filter text (text after trigger).
     */
    var filterText: String
        get() = core.filterText
        internal set(value) {
            core.filterText = value
        }

    /**
     * Options that match the current filter.
     */
    var filteredOptions: List<CommandOption<T>>
        get() = core.filteredOptions
        internal set(value) {
            core.updateFilteredOptions(value)
        }

    /**
     * Currently selected option, if any.
     */
    val selectedOption: CommandOption<T>?
        get() = core.selectedOption

    /**
     * Whether the filter has any results.
     */
    val hasResults: Boolean
        get() = core.hasResults

    internal fun updateFilteredOptions(options: List<CommandOption<T>>) {
        core.updateFilteredOptions(options)
    }

    internal fun ensureSelectionInBounds() {
        core.ensureSelectionInBounds()
    }

    internal fun resetSelection() {
        core.resetSelection()
    }

    internal fun moveUp() {
        core.moveUp()
    }

    internal fun moveDown() {
        core.moveDown()
    }
}

/**
 * Remember a command palette state.
 */
@Composable
fun <T> rememberCommandPaletteState(
    initialVisible: Boolean = false,
    initialSelectedIndex: Int = 0,
): CommandPaletteState<T> = remember { CommandPaletteState(initialVisible, initialSelectedIndex) }

/**
 * A command palette widget that displays filterable options with keyboard navigation.
 *
 * The palette automatically shows when the input starts with the trigger character
 * and hides when the trigger is removed or a space is typed after it.
 *
 * Keyboard navigation is handled via [LocalKeyboardInterceptor], which allows
 * the palette to intercept Arrow/Enter/Escape keys when visible.
 *
 * Example:
 * ```kotlin
 * val commands = listOf(
 *     CommandOption(label = "clear", description = "Clear chat", data = "clear"),
 *     CommandOption(label = "help", description = "Show help", data = "help"),
 * )
 *
 * CommandPalette(
 *     options = commands,
 *     inputValue = inputText,
 *     onOptionSelected = { option ->
 *         handleSelection(option)
 *     },
 *     onInputTransform = { newInput ->
 *         viewModel.onInputChanged(newInput)
 *     },
 * )
 * ```
 *
 * @param T The type of data associated with options.
 * @param options List of available command options.
 * @param inputValue Current input text to detect trigger character.
 * @param onOptionSelected Callback when an option is selected via Enter key.
 * @param onInputTransform Callback to transform input (e.g., remove trigger text after selection).
 * @param modifier Modifiers to apply.
 * @param triggerChar Character that triggers the palette (default '/').
 * @param commandPrefix Prefix added to command labels (default uses [triggerChar]).
 * @param selectionIndicator Text prefix shown for the selected item. Set to null to hide (default).
 * @param visibleCount Maximum number of visible options (default 5).
 * @param textStyles Text styling configuration.
 * @param showDescriptions Whether to show option descriptions.
 * @param showIcons Whether to show option icons.
 * @param noResultsText Text shown when no options match the filter.
 * @param enabled Whether the palette responds to input.
 * @param state State holder for visibility, selection, and filtering.
 */
@Composable
fun <T> CommandPalette(
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
    // Remember callbacks to avoid stale closures
    val onOptionSelectedCallback = rememberCallback(onOptionSelected)
    val onInputTransformCallback = rememberCallback(onInputTransform)

    // Get keyboard interceptor for registering handlers
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    // Detect trigger character at the start and extract filter text
    val hasTrigger = inputValue.startsWith(triggerChar)
    val triggerIndex = if (hasTrigger) 0 else -1
    val textAfterTrigger = if (hasTrigger) inputValue.substring(1) else ""
    val shouldShowPalette = hasTrigger && !textAfterTrigger.contains(' ')
    val filterText = if (shouldShowPalette) textAfterTrigger else ""

    // Filter options based on filter text
    val filteredOptions =
        if (filterText.isEmpty()) {
            options.filter { it.enabled }
        } else {
            options.filter { option ->
                option.enabled &&
                    (
                        option.label.contains(filterText, ignoreCase = true) ||
                            option.description.contains(filterText, ignoreCase = true)
                    )
            }
        }

    SideEffect {
        if (state.isVisible != shouldShowPalette) state.resetSelection()
        state.isVisible = shouldShowPalette
        state.filterText = filterText
        state.updateFilteredOptions(filteredOptions)
    }

    val labelWidth =
        if (filteredOptions.isEmpty()) {
            0
        } else {
            filteredOptions.maxOf { option ->
                commandLabelFor(option, commandPrefix).length
            }
        }

    // Register keyboard handler when visible
    DisposableEffect(listOf(shouldShowPalette, enabled, keyboardInterceptor)) {
        if (!shouldShowPalette || !enabled) {
            return@DisposableEffect onDispose {}
        }

        val dispose =
            keyboardInterceptor.register { event ->
                handleSelectorKeyEvent(
                    event,
                    SelectorKeyBindings(
                        onMoveUp = { state.moveUp() },
                        onMoveDown = { state.moveDown() },
                        onConfirm = {
                            val selected = state.selectedOption
                            if (selected == null) {
                                false
                            } else {
                                val beforeTrigger =
                                    if (triggerIndex >= 0) {
                                        inputValue.substring(0, triggerIndex)
                                    } else {
                                        inputValue
                                    }
                                onInputTransformCallback(beforeTrigger)
                                onOptionSelectedCallback(selected)
                                state.isVisible = false
                                state.resetSelection()
                                true
                            }
                        },
                        onTab = {
                            val selected = state.selectedOption
                            if (selected == null) {
                                false
                            } else {
                                val beforeTrigger =
                                    if (triggerIndex >= 0) {
                                        inputValue.substring(0, triggerIndex)
                                    } else {
                                        inputValue
                                    }
                                val commandText = commandLabelFor(selected, commandPrefix)
                                val completion =
                                    if (commandText.endsWith(" ")) {
                                        beforeTrigger + commandText
                                    } else {
                                        beforeTrigger + commandText + " "
                                    }
                                onInputTransformCallback(completion)
                                state.isVisible = false
                                state.resetSelection()
                                true
                            }
                        },
                        onCancel = {
                            state.isVisible = false
                            state.resetSelection()
                            true
                        },
                    ),
                )
            }

        onDispose { dispose() }
    }

    // Only render if visible
    if (!shouldShowPalette) return

    // Calculate visible window for scrolling
    val windowSlice = computeWindowSlice(filteredOptions, state.selectedIndex, visibleCount)
    val visibleOptions = windowSlice.window

    // Render the palette
    Column(modifier = modifier) {
        if (visibleOptions.isEmpty()) {
            Text(
                text = noResultsText,
                style = textStyles.noResultsText,
            )
        } else {
            visibleOptions.forEachIndexed { localIndex, option ->
                val isSelected = localIndex == windowSlice.localSelected

                CommandPaletteItem(
                    option = option,
                    isSelected = isSelected,
                    showDescription = showDescriptions,
                    showIcon = showIcons,
                    textStyles = textStyles,
                    selectionIndicator = selectionIndicator,
                    commandPrefix = commandPrefix,
                    labelWidth = labelWidth,
                )
            }
        }
    }
}

/**
 * Renders a single command palette option.
 */
@Composable
private fun <T> CommandPaletteItem(
    option: CommandOption<T>,
    isSelected: Boolean,
    showDescription: Boolean,
    showIcon: Boolean,
    textStyles: CommandPaletteTextStyles,
    selectionIndicator: String?,
    commandPrefix: String?,
    labelWidth: Int,
) {
    val prefixText =
        selectionIndicator?.takeIf { it.isNotEmpty() }?.let { indicator ->
            if (isSelected) indicator else " ".repeat(indicator.length)
        }
    val prefixStyle = if (isSelected) textStyles.selectedPrefix else textStyles.prefix

    val labelStyle =
        when {
            !option.enabled -> textStyles.disabledLabel
            isSelected -> textStyles.selectedLabel
            else -> textStyles.label
        }

    val descriptionStyle =
        if (isSelected) {
            textStyles.selectedDescription
        } else {
            textStyles.description
        }

    val displayLabel = commandLabelFor(option, commandPrefix)
    val labelText =
        if (showDescription && option.description.isNotEmpty() && labelWidth > 0) {
            displayLabel.padEnd(labelWidth)
        } else {
            displayLabel
        }

    Row {
        if (!prefixText.isNullOrEmpty()) {
            Text(text = prefixText, style = prefixStyle)
        }

        // Icon (if enabled and present)
        if (showIcon && !option.icon.isNullOrEmpty()) {
            Text(text = "${option.icon} ", style = labelStyle)
        }

        // Label
        Text(text = labelText, style = labelStyle)

        // Description (if enabled and present)
        if (showDescription && option.description.isNotEmpty()) {
            Text(text = " ".repeat(15), style = descriptionStyle)
            Text(text = option.description, style = descriptionStyle)
        }
    }
}

private fun <T> commandLabelFor(
    option: CommandOption<T>,
    commandPrefix: String?,
): String =
    if (!commandPrefix.isNullOrEmpty() && !option.label.startsWith(commandPrefix)) {
        commandPrefix + option.label
    } else {
        option.label
    }
