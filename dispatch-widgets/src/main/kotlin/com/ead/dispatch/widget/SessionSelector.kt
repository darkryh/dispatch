package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.rememberCallback
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * Represents a single session option in the selector.
 *
 * @param T The type of data associated with this option.
 * @param id Unique session identifier.
 * @param title Session title or conversation preview.
 * @param updatedTime Pre-formatted relative time string (e.g., "15 hours ago").
 * @param conversationId Display version of conversation ID (may be truncated).
 * @param messageCount Number of messages in the session.
 * @param data The data payload associated with this option.
 * @param enabled Whether this option can be selected.
 */
data class SessionOption<T>(
    val id: String,
    val title: String,
    val updatedTime: String,
    val conversationId: String,
    val messageCount: Int = 0,
    val data: T,
    val enabled: Boolean = true,
)

/**
 * Enum defining which columns to display in the SessionSelector.
 */
enum class SessionDisplayColumn {
    /** Relative time since last update */
    UPDATED_TIME,

    /** Unique conversation/session ID */
    CONVERSATION_ID,

    /** Session title or conversation preview */
    TITLE,

    /** Number of messages in the session */
    MESSAGE_COUNT,
}

/**
 * Configuration for a column's width and alignment.
 *
 * @param width Fixed width for the column (null = auto-width based on content).
 * @param alignment Text alignment within the column.
 */
data class ColumnConfig(
    val width: Int? = null,
    val alignment: ColumnAlignment = ColumnAlignment.LEFT,
)

/**
 * Text alignment options for columns.
 */
enum class ColumnAlignment {
    LEFT,
    RIGHT,
    CENTER,
}

/**
 * Text styles for the session selector.
 *
 * @param prefix Style for the selection prefix of unselected items.
 * @param selectedPrefix Style for the selection prefix of the selected item.
 * @param updatedTime Style for the updated time column.
 * @param selectedUpdatedTime Style for the updated time column when selected.
 * @param conversationId Style for the conversation ID column.
 * @param selectedConversationId Style for the conversation ID column when selected.
 * @param title Style for the title column.
 * @param selectedTitle Style for the title column when selected.
 * @param messageCount Style for the message count column.
 * @param selectedMessageCount Style for the message count column when selected.
 * @param noResultsText Style for the "no results" message.
 * @param filterPrompt Style for the filter prompt.
 */
data class SessionSelectorTextStyles(
    val prefix: TextStyle? = rgb("#6F7279"),
    val selectedPrefix: TextStyle? = rgb("#00BFFF") + TextStyle(bold = true),
    val updatedTime: TextStyle? = rgb("#898D92"),
    val selectedUpdatedTime: TextStyle? = rgb("#00BFFF"),
    val conversationId: TextStyle? = rgb("#6F7279"),
    val selectedConversationId: TextStyle? = rgb("#00BFFF"),
    val title: TextStyle? = rgb("#ffffff"),
    val selectedTitle: TextStyle? = rgb("#00BFFF") + TextStyle(bold = true),
    val messageCount: TextStyle? = rgb("#6F7279"),
    val selectedMessageCount: TextStyle? = rgb("#00BFFF"),
    val noResultsText: TextStyle? = rgb("#898D92"),
    val filterPrompt: TextStyle? = rgb("#ffffff"),
    val header: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
)

/**
 * Default header labels for each column.
 */
object SessionColumnHeaders {
    val UPDATED_TIME = "Updated"
    val CONVERSATION_ID = "ID"
    val TITLE = "Conversation"
    val MESSAGE_COUNT = "Messages"
}

/**
 * State holder for the session selector.
 */
class SessionSelectorState<T>(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
) {
    private val core = FilterableSelectorState<SessionOption<T>>(initialVisible, initialSelectedIndex)

    /**
     * Whether the selector is currently visible.
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
     * Current filter text for search.
     */
    var filterText: String
        get() = core.filterText
        internal set(value) {
            core.filterText = value
        }

    /**
     * Options that match the current filter.
     */
    var filteredOptions: List<SessionOption<T>>
        get() = core.filteredOptions
        internal set(value) {
            core.updateFilteredOptions(value)
        }

    /**
     * Currently selected option, if any.
     */
    val selectedOption: SessionOption<T>?
        get() = core.selectedOption

    /**
     * Whether the filter has any results.
     */
    val hasResults: Boolean
        get() = core.hasResults

    /**
     * Move selection up by one.
     */
    fun moveUp() {
        core.moveUp()
    }

    /**
     * Move selection down by one.
     */
    fun moveDown() {
        core.moveDown()
    }

    /**
     * Append a character to the filter text.
     */
    fun appendFilter(char: Char) {
        core.appendFilter(char)
    }

    /**
     * Remove the last character from filter text.
     */
    fun removeLastFilter() {
        core.removeLastFilter()
    }

    /**
     * Clear the filter text.
     */
    fun clearFilter() {
        core.clearFilter()
    }

    internal fun updateFilteredOptions(options: List<SessionOption<T>>) {
        core.updateFilteredOptions(options)
    }
}

/**
 * Remember a session selector state.
 */
@Composable
fun <T> rememberSessionSelectorState(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
): SessionSelectorState<T> = remember { SessionSelectorState(initialVisible, initialSelectedIndex) }

/**
 * A session selector widget that displays filterable session options with keyboard navigation.
 *
 * Supports configurable columns to display different session attributes.
 * Keyboard navigation is handled internally:
 * - Arrow Up/Down: Navigate through options
 * - Enter: Select current option
 * - Escape: Exit/cancel
 * - Character input: Filter/search (when showFilter is true)
 * - Backspace: Remove filter character
 *
 * Example:
 * ```kotlin
 * val sessions = listOf(
 *     SessionOption(
 *         id = "conv_123",
 *         title = "Discussing Kotlin coroutines",
 *         updatedTime = "15 hours ago",
 *         conversationId = "conv_123...",
 *         data = sessionData,
 *     ),
 * )
 *
 * SessionSelector(
 *     options = sessions,
 *     onOptionSelected = { option -> handleSelection(option) },
 *     onExit = { navController.popBackStack() },
 *     columns = listOf(
 *         SessionDisplayColumn.UPDATED_TIME,
 *         SessionDisplayColumn.CONVERSATION_ID,
 *         SessionDisplayColumn.TITLE,
 *     ),
 * )
 * ```
 *
 * @param T The type of data associated with options.
 * @param options List of available session options.
 * @param onOptionSelected Callback when an option is selected via Enter key.
 * @param onExit Callback when the selector is exited via Escape key.
 * @param modifier Modifiers to apply.
 * @param columns List of columns to display in order.
 * @param columnConfigs Optional configuration for column widths/alignment.
 * @param selectionIndicator Text prefix shown for the selected item.
 * @param visibleCount Maximum number of visible options.
 * @param textStyles Text styling configuration.
 * @param showFilter Whether to enable search/filter functionality.
 * @param showHeaders Whether to show column headers.
 * @param headerLabels Custom header labels for columns (uses defaults if not provided).
 * @param noResultsText Text shown when no options match the filter.
 * @param enabled Whether the selector responds to input.
 * @param state State holder for visibility, selection, and filtering.
 */
@Composable
fun <T> SessionSelector(
    options: List<SessionOption<T>>,
    onOptionSelected: (SessionOption<T>) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    columns: List<SessionDisplayColumn> =
        listOf(
            SessionDisplayColumn.UPDATED_TIME,
            SessionDisplayColumn.CONVERSATION_ID,
            SessionDisplayColumn.TITLE,
        ),
    columnConfigs: Map<SessionDisplayColumn, ColumnConfig> = emptyMap(),
    selectionIndicator: String? = "> ",
    visibleCount: Int = 10,
    textStyles: SessionSelectorTextStyles = SessionSelectorTextStyles(),
    showFilter: Boolean = true,
    showHeaders: Boolean = true,
    headerLabels: Map<SessionDisplayColumn, String> = emptyMap(),
    noResultsText: String = "No matching sessions",
    enabled: Boolean = true,
    state: SessionSelectorState<T> = rememberSessionSelectorState(),
) {
    // Remember callbacks to avoid stale closures
    val onOptionSelectedCallback = rememberCallback(onOptionSelected)
    val onExitCallback = rememberCallback(onExit)

    // Register keyboard handler via DispatchScope
    val keyboardInterceptor = LocalKeyboardInterceptor.current

    // Filter options based on filter text
    val filteredOptions =
        if (state.filterText.isEmpty()) {
            options.filter { it.enabled }
        } else {
            options.filter { option ->
                option.enabled &&
                    (
                        option.title.contains(state.filterText, ignoreCase = true) ||
                            option.conversationId.contains(state.filterText, ignoreCase = true) ||
                            option.id.contains(state.filterText, ignoreCase = true)
                    )
            }
        }
    state.updateFilteredOptions(filteredOptions)

    // Calculate column widths based on content and headers
    val columnWidths = calculateColumnWidths(filteredOptions, columns, columnConfigs, headerLabels, showHeaders)

    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        val consumed =
            handleSelectorKeyEvent(
                event,
                SelectorKeyBindings(
                    onMoveUp = { state.moveUp() },
                    onMoveDown = { state.moveDown() },
                    onConfirm = {
                        state.selectedOption?.let(onOptionSelectedCallback)
                        true
                    },
                    onCancel = {
                        onExitCallback()
                        true
                    },
                    onBackspace = {
                        if (showFilter) {
                            state.removeLastFilter()
                        }
                        true
                    },
                    onCharacter =
                        if (showFilter) {
                            { char ->
                                state.appendFilter(char)
                                true
                            }
                        } else {
                            null
                        },
                ),
            )

        return consumed
    }

    // Register keyboard handler
    DisposableEffect(listOf(state, state.isVisible, enabled, showFilter, keyboardInterceptor)) {
        if (!state.isVisible || !enabled) {
            return@DisposableEffect onDispose {}
        }

        val interceptorDispose =
            keyboardInterceptor.register { event ->
                handleKeyEvent(event)
            }

        onDispose {
            interceptorDispose()
        }
    }

    // Only render if visible
    if (!state.isVisible) return

    // Calculate visible window for scrolling
    val windowSlice = computeWindowSlice(filteredOptions, state.selectedIndex, visibleCount)
    val visibleOptions = windowSlice.window

    // Render the selector
    Column(modifier = modifier) {
        // Show filter text if enabled and has content
        if (showFilter && state.filterText.isNotEmpty()) {
            Row {
                Text(
                    text = "Search: ${state.filterText}",
                    style = textStyles.filterPrompt,
                )
            }
        }

        // Show column headers
        if (showHeaders) {
            SessionSelectorHeader(
                columns = columns,
                columnWidths = columnWidths,
                textStyles = textStyles,
                selectionIndicator = selectionIndicator,
                headerLabels = headerLabels,
            )
        }

        if (visibleOptions.isEmpty()) {
            Text(
                text = noResultsText,
                style = textStyles.noResultsText,
            )
        } else {
            visibleOptions.forEachIndexed { localIndex, option ->
                val isSelected = localIndex == windowSlice.localSelected

                SessionSelectorItem(
                    option = option,
                    isSelected = isSelected,
                    columns = columns,
                    columnWidths = columnWidths,
                    textStyles = textStyles,
                    selectionIndicator = selectionIndicator,
                )
            }
        }
    }
}

/**
 * Calculate column widths based on content and headers.
 */
private fun <T> calculateColumnWidths(
    options: List<SessionOption<T>>,
    columns: List<SessionDisplayColumn>,
    columnConfigs: Map<SessionDisplayColumn, ColumnConfig>,
    headerLabels: Map<SessionDisplayColumn, String>,
    showHeaders: Boolean,
): Map<SessionDisplayColumn, Int> =
    columns.associateWith { column ->
        // Check if there's a configured width
        columnConfigs[column]?.width ?: run {
            // Get header width
            val headerWidth =
                if (showHeaders) {
                    val headerText =
                        headerLabels[column] ?: when (column) {
                            SessionDisplayColumn.UPDATED_TIME -> SessionColumnHeaders.UPDATED_TIME
                            SessionDisplayColumn.CONVERSATION_ID -> SessionColumnHeaders.CONVERSATION_ID
                            SessionDisplayColumn.TITLE -> SessionColumnHeaders.TITLE
                            SessionDisplayColumn.MESSAGE_COUNT -> SessionColumnHeaders.MESSAGE_COUNT
                        }
                    headerText.length
                } else {
                    0
                }

            // Get max content width
            val contentWidth =
                if (options.isEmpty()) {
                    0
                } else {
                    options.maxOf { option ->
                        when (column) {
                            SessionDisplayColumn.UPDATED_TIME -> option.updatedTime.length
                            SessionDisplayColumn.CONVERSATION_ID -> option.conversationId.length
                            SessionDisplayColumn.TITLE -> option.title.length.coerceAtMost(50) // Cap title width
                            SessionDisplayColumn.MESSAGE_COUNT -> "${option.messageCount} msgs".length
                        }
                    }
                }

            // Use the larger of header or content width
            maxOf(headerWidth, contentWidth)
        }
    }

/**
 * Renders a single session selector item.
 */
@Composable
private fun <T> SessionSelectorItem(
    option: SessionOption<T>,
    isSelected: Boolean,
    columns: List<SessionDisplayColumn>,
    columnWidths: Map<SessionDisplayColumn, Int>,
    textStyles: SessionSelectorTextStyles,
    selectionIndicator: String?,
) {
    val prefixText =
        selectionIndicator?.takeIf { it.isNotEmpty() }?.let { indicator ->
            if (isSelected) indicator else " ".repeat(indicator.length)
        }
    val prefixStyle = if (isSelected) textStyles.selectedPrefix else textStyles.prefix

    Row {
        if (!prefixText.isNullOrEmpty()) {
            Text(text = prefixText, style = prefixStyle)
        }

        columns.forEachIndexed { index, column ->
            val (text, style) =
                when (column) {
                    SessionDisplayColumn.UPDATED_TIME -> {
                        val s = if (isSelected) textStyles.selectedUpdatedTime else textStyles.updatedTime
                        option.updatedTime to s
                    }
                    SessionDisplayColumn.CONVERSATION_ID -> {
                        val s = if (isSelected) textStyles.selectedConversationId else textStyles.conversationId
                        option.conversationId to s
                    }
                    SessionDisplayColumn.TITLE -> {
                        val s = if (isSelected) textStyles.selectedTitle else textStyles.title
                        option.title to s
                    }
                    SessionDisplayColumn.MESSAGE_COUNT -> {
                        val s = if (isSelected) textStyles.selectedMessageCount else textStyles.messageCount
                        "${option.messageCount} msgs" to s
                    }
                }

            val width = columnWidths[column] ?: text.length
            val paddedText = text.take(width).padEnd(width)
            Text(text = paddedText, style = style)

            // Add spacing between columns (except after last)
            if (index < columns.size - 1) {
                Text(text = "  ") // Column separator
            }
        }
    }
}

/**
 * Renders the header row for the session selector.
 */
@Composable
private fun SessionSelectorHeader(
    columns: List<SessionDisplayColumn>,
    columnWidths: Map<SessionDisplayColumn, Int>,
    textStyles: SessionSelectorTextStyles,
    selectionIndicator: String?,
    headerLabels: Map<SessionDisplayColumn, String>,
) {
    // Empty prefix space to align with selection indicator
    val prefixSpace =
        selectionIndicator?.takeIf { it.isNotEmpty() }?.let { indicator ->
            " ".repeat(indicator.length)
        }

    Row {
        if (!prefixSpace.isNullOrEmpty()) {
            Text(text = prefixSpace, style = textStyles.header)
        }

        columns.forEachIndexed { index, column ->
            val headerText =
                headerLabels[column] ?: when (column) {
                    SessionDisplayColumn.UPDATED_TIME -> SessionColumnHeaders.UPDATED_TIME
                    SessionDisplayColumn.CONVERSATION_ID -> SessionColumnHeaders.CONVERSATION_ID
                    SessionDisplayColumn.TITLE -> SessionColumnHeaders.TITLE
                    SessionDisplayColumn.MESSAGE_COUNT -> SessionColumnHeaders.MESSAGE_COUNT
                }

            val width = columnWidths[column] ?: headerText.length
            val paddedText = headerText.take(width).padEnd(width)
            Text(text = paddedText, style = textStyles.header)

            // Add spacing between columns (except after last)
            if (index < columns.size - 1) {
                Text(text = "  ") // Column separator
            }
        }
    }
}
