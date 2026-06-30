package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.runtime.LocalKeyboardInterceptor
import io.github.darkryh.dispatch.runtime.LocalTerminalWidth
import io.github.darkryh.dispatch.runtime.rememberCallback

/**
 * Describes how a [TableColumn] is sized when the table is laid out.
 *
 * A column never writes outside its resolved width; the renderer clips and pads each cell to an
 * integer number of terminal cells so the grid stays aligned regardless of content length.
 */
sealed interface TableColumnWidth {
    /**
     * Size the column to the widest cell (and its header, when headers are shown).
     */
    data object Auto : TableColumnWidth

    /**
     * Pin the column to an exact number of terminal cells.
     *
     * @param chars The fixed width, in terminal cells.
     */
    data class Fixed(
        val chars: Int,
    ) : TableColumnWidth

    /**
     * Distribute the remaining terminal width across all weighted columns proportionally.
     *
     * The leftover width (terminal width minus the selection indicator, separators and the sum of
     * the [Fixed]/[Auto] columns) is split between weighted columns in proportion to their weight.
     *
     * @param weight The relative weight of this column among all weighted columns.
     */
    data class Weight(
        val weight: Float,
    ) : TableColumnWidth
}

/**
 * Describes a single, caller-defined column of a [Table] or [FilterableTable].
 *
 * Columns are fully generic: the table never inspects the row type, it only renders the string
 * returned by [valueOf]. This keeps the table domain-neutral — there is no built-in vocabulary for
 * any particular kind of row.
 *
 * @param T The row type rendered by the owning table.
 * @param header The label shown in the header row for this column.
 * @param width How the column is sized. Defaults to [TableColumnWidth.Auto].
 * @param align How the cell text is aligned within the column. Defaults to [TextAlign.LEFT].
 * @param valueOf Projects a row into the string displayed in this column.
 */
data class TableColumn<T>(
    val header: String,
    val width: TableColumnWidth = TableColumnWidth.Auto,
    val align: TextAlign = TextAlign.LEFT,
    val valueOf: (T) -> String,
)

/**
 * A static, read-only data table.
 *
 * Column widths are computed from the content (and headers, when shown); [TableColumnWidth.Weight]
 * columns share the leftover terminal width read from [LocalTerminalWidth]. When [maxVisibleRows] is
 * set, only the first that-many rows are rendered. The table performs no direct terminal writes — it
 * is a pure measure/compose widget, so it never flickers.
 *
 * Example:
 * ```kotlin
 * Table(
 *     items = users,
 *     columns = listOf(
 *         TableColumn(header = "Name", valueOf = { it.name }),
 *         TableColumn(header = "Email", width = TableColumnWidth.Weight(1f), valueOf = { it.email }),
 *         TableColumn(header = "Age", align = TextAlign.RIGHT, valueOf = { it.age.toString() }),
 *     ),
 * )
 * ```
 *
 * @param T The row type.
 * @param items Rows to render in order.
 * @param columns Column definitions, rendered left-to-right.
 * @param modifier Modifiers to apply to the table column.
 * @param showHeaders Whether to render the header row.
 * @param headerStyle Style applied to the header row text.
 * @param rowStyle Style applied to data-row text.
 * @param maxVisibleRows Maximum number of rows to render (null = render all rows).
 */
@Composable
fun <T> Table(
    items: List<T>,
    columns: List<TableColumn<T>>,
    modifier: Modifier = Modifier,
    showHeaders: Boolean = true,
    headerStyle: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
    rowStyle: TextStyle? = rgb("#ffffff"),
    maxVisibleRows: Int? = null,
) {
    if (columns.isEmpty()) return

    val terminalWidth = LocalTerminalWidth.current
    val columnWidths = computeTableColumnWidths(items, columns, showHeaders, terminalWidth, reservedWidth = 0)

    val visibleItems =
        if (maxVisibleRows != null && maxVisibleRows >= 0) {
            items.take(maxVisibleRows)
        } else {
            items
        }

    Column(modifier = modifier) {
        if (showHeaders) {
            TableHeaderRow(
                columns = columns,
                columnWidths = columnWidths,
                headerStyle = headerStyle,
                prefix = null,
            )
        }

        visibleItems.forEach { item ->
            TableDataRow(
                item = item,
                columns = columns,
                columnWidths = columnWidths,
                rowStyle = rowStyle,
                prefix = null,
            )
        }
    }
}

/**
 * State holder for [FilterableTable].
 *
 * Mirrors the structure of the session selector's state holder: it wraps the shared, internal
 * [FilterableSelectorState] and exposes only the navigation/filter operations the table needs, so
 * the public surface stays domain-neutral.
 *
 * @param T The row type.
 * @param initialVisible Whether the table starts visible.
 * @param initialSelectedIndex The initially selected row index within the filtered rows.
 */
class FilterableTableState<T>(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
) {
    private val core = FilterableSelectorState<T>(initialVisible, initialSelectedIndex)

    /**
     * Whether the table is currently visible.
     */
    var isVisible: Boolean
        get() = core.isVisible
        internal set(value) {
            core.isVisible = value
        }

    /**
     * Currently selected row index within [filteredItems].
     */
    var selectedIndex: Int
        get() = core.selectedIndex
        internal set(value) {
            core.selectedIndex = value
        }

    /**
     * Current filter text.
     */
    var filterText: String
        get() = core.filterText
        internal set(value) {
            core.filterText = value
        }

    /**
     * Rows that match the current filter.
     */
    val filteredItems: List<T>
        get() = core.filteredOptions

    /**
     * Currently selected row, if any.
     */
    val selectedItem: T?
        get() = core.selectedOption

    /**
     * Whether the filter currently has any matching rows.
     */
    val hasResults: Boolean
        get() = core.hasResults

    /**
     * Move the selection up by one.
     */
    fun moveUp() {
        core.moveUp()
    }

    /**
     * Move the selection down by one.
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
     * Remove the last character from the filter text.
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

    internal fun updateFilteredItems(items: List<T>) {
        core.updateFilteredOptions(items)
    }
}

/**
 * Remember a [FilterableTableState].
 *
 * @param T The row type.
 * @param initialVisible Whether the table starts visible.
 * @param initialSelectedIndex The initially selected row index.
 */
@Composable
fun <T> rememberTableState(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
): FilterableTableState<T> = remember { FilterableTableState(initialVisible, initialSelectedIndex) }

/**
 * An interactive, filterable data table with keyboard navigation.
 *
 * This is the generic, domain-neutral counterpart to a session selector: the columns are entirely
 * caller-defined. A filter line is shown (when [showFilter] is enabled and the filter is non-empty),
 * rows are windowed with the same slice math as the session selector, and keyboard handling is
 * registered through [LocalKeyboardInterceptor]:
 * - Arrow Up/Down: move the selection
 * - Enter: confirm the selected row ([onRowSelected])
 * - Escape: exit ([onExit])
 * - Character input: append to the filter (when [showFilter] is true)
 * - Backspace: remove the last filter character
 *
 * Filter input is handled inline through the selector's character/backspace bindings, so the table
 * has no dependency on any text-field widget.
 *
 * Example:
 * ```kotlin
 * FilterableTable(
 *     items = users,
 *     columns = listOf(
 *         TableColumn(header = "Name", valueOf = { it.name }),
 *         TableColumn(header = "Email", width = TableColumnWidth.Weight(1f), valueOf = { it.email }),
 *     ),
 *     onRowSelected = { open(it) },
 *     onExit = { back() },
 * )
 * ```
 *
 * @param T The row type.
 * @param items All rows available to the table.
 * @param columns Column definitions, rendered left-to-right.
 * @param onRowSelected Callback invoked with the selected row on Enter.
 * @param onExit Callback invoked on Escape.
 * @param modifier Modifiers to apply to the table column.
 * @param filterPredicate Predicate deciding whether a row matches the current filter text. Defaults
 *   to a case-insensitive substring match over every column's projected value.
 * @param showFilter Whether character input filters the rows.
 * @param showHeaders Whether to render the header row.
 * @param visibleCount Maximum number of rows visible in the scroll window.
 * @param selectionIndicator Text prefix shown for the selected row (empty disables the prefix).
 * @param headerStyle Style applied to the header row text.
 * @param rowStyle Style applied to unselected data-row text.
 * @param selectedRowStyle Style applied to the selected data-row text.
 * @param prefixStyle Style applied to the selection-indicator prefix of unselected rows.
 * @param selectedPrefixStyle Style applied to the selection-indicator prefix of the selected row.
 * @param filterPromptStyle Style applied to the filter prompt line.
 * @param noResultsStyle Style applied to the "no results" message.
 * @param noResultsText Text shown when no rows match the filter.
 * @param enabled Whether the table responds to keyboard input.
 * @param state State holder for visibility, selection and filtering.
 */
@Composable
fun <T> FilterableTable(
    items: List<T>,
    columns: List<TableColumn<T>>,
    onRowSelected: (T) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    filterPredicate: (T, String) -> Boolean = { item, query ->
        columns.any { column -> column.valueOf(item).contains(query, ignoreCase = true) }
    },
    showFilter: Boolean = true,
    showHeaders: Boolean = true,
    visibleCount: Int = 10,
    selectionIndicator: String = "> ",
    headerStyle: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
    rowStyle: TextStyle? = rgb("#ffffff"),
    selectedRowStyle: TextStyle? = rgb("#00BFFF") + TextStyle(bold = true),
    prefixStyle: TextStyle? = rgb("#6F7279"),
    selectedPrefixStyle: TextStyle? = rgb("#00BFFF") + TextStyle(bold = true),
    filterPromptStyle: TextStyle? = rgb("#ffffff"),
    noResultsStyle: TextStyle? = rgb("#898D92"),
    noResultsText: String = "No matching rows",
    enabled: Boolean = true,
    state: FilterableTableState<T> = rememberTableState(),
) {
    if (columns.isEmpty()) return

    // Remember callbacks to avoid stale closures.
    val onRowSelectedCallback = rememberCallback(onRowSelected)
    val onExitCallback = rememberCallback(onExit)

    val keyboardInterceptor = LocalKeyboardInterceptor.current

    // Filter rows based on the filter text.
    val filteredItems =
        if (state.filterText.isEmpty()) {
            items
        } else {
            items.filter { filterPredicate(it, state.filterText) }
        }
    state.updateFilteredItems(filteredItems)

    val terminalWidth = LocalTerminalWidth.current
    val indicatorWidth = selectionIndicator.takeIf { it.isNotEmpty() }?.length ?: 0
    val columnWidths = computeTableColumnWidths(filteredItems, columns, showHeaders, terminalWidth, indicatorWidth)

    fun handleKeyEvent(event: KeyboardEvent): Boolean =
        handleSelectorKeyEvent(
            event,
            SelectorKeyBindings(
                onMoveUp = { state.moveUp() },
                onMoveDown = { state.moveDown() },
                onConfirm = {
                    state.selectedItem?.let(onRowSelectedCallback)
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

    // Register keyboard handler.
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

    if (!state.isVisible) return

    // Calculate the visible window for scrolling.
    val windowSlice = computeWindowSlice(filteredItems, state.selectedIndex, visibleCount)
    val visibleItems = windowSlice.window

    Column(modifier = modifier) {
        if (showFilter && state.filterText.isNotEmpty()) {
            Row {
                Text(
                    text = "Search: ${state.filterText}",
                    style = filterPromptStyle,
                )
            }
        }

        if (showHeaders) {
            TableHeaderRow(
                columns = columns,
                columnWidths = columnWidths,
                headerStyle = headerStyle,
                prefix = selectionIndicator.takeIf { it.isNotEmpty() }?.let { " ".repeat(it.length) },
            )
        }

        if (visibleItems.isEmpty()) {
            Text(text = noResultsText, style = noResultsStyle)
        } else {
            visibleItems.forEachIndexed { localIndex, item ->
                val isSelected = localIndex == windowSlice.localSelected
                val prefix =
                    selectionIndicator.takeIf { it.isNotEmpty() }?.let { indicator ->
                        if (isSelected) indicator else " ".repeat(indicator.length)
                    }
                TableDataRow(
                    item = item,
                    columns = columns,
                    columnWidths = columnWidths,
                    rowStyle = if (isSelected) selectedRowStyle else rowStyle,
                    prefix = prefix,
                    prefixStyle = if (isSelected) selectedPrefixStyle else prefixStyle,
                )
            }
        }
    }
}

/**
 * Renders the header row for a table.
 */
@Composable
private fun <T> TableHeaderRow(
    columns: List<TableColumn<T>>,
    columnWidths: List<Int>,
    headerStyle: TextStyle?,
    prefix: String?,
) {
    Row {
        if (!prefix.isNullOrEmpty()) {
            Text(text = prefix, style = headerStyle)
        }
        columns.forEachIndexed { index, column ->
            val width = columnWidths.getOrElse(index) { column.header.length }
            Text(text = alignTableCell(column.header, width, column.align), style = headerStyle)
            if (index < columns.lastIndex) {
                Text(text = TABLE_COLUMN_SEPARATOR)
            }
        }
    }
}

/**
 * Renders a single data row for a table.
 */
@Composable
private fun <T> TableDataRow(
    item: T,
    columns: List<TableColumn<T>>,
    columnWidths: List<Int>,
    rowStyle: TextStyle?,
    prefix: String?,
    prefixStyle: TextStyle? = rowStyle,
) {
    Row {
        if (!prefix.isNullOrEmpty()) {
            Text(text = prefix, style = prefixStyle)
        }
        columns.forEachIndexed { index, column ->
            val text = column.valueOf(item)
            val width = columnWidths.getOrElse(index) { text.length }
            Text(text = alignTableCell(text, width, column.align), style = rowStyle)
            if (index < columns.lastIndex) {
                Text(text = TABLE_COLUMN_SEPARATOR)
            }
        }
    }
}

/**
 * Two-space gap rendered between adjacent table columns.
 */
private const val TABLE_COLUMN_SEPARATOR = "  "

/**
 * Clip [text] to [width] cells and pad it according to [align].
 *
 * Always returns a string of exactly [width] visible cells so columns stay aligned.
 */
internal fun alignTableCell(
    text: String,
    width: Int,
    align: TextAlign,
): String {
    if (width <= 0) return ""
    val clipped = text.take(width)
    return when (align) {
        TextAlign.RIGHT -> clipped.padStart(width)
        TextAlign.CENTER -> {
            val total = width - clipped.length
            val left = total / 2
            val right = total - left
            " ".repeat(left) + clipped + " ".repeat(right)
        }
        else -> clipped.padEnd(width)
    }
}

/**
 * Resolve the rendered width of every column.
 *
 * [TableColumnWidth.Fixed] columns use their pinned width, [TableColumnWidth.Auto] columns grow to
 * the widest cell/header, and [TableColumnWidth.Weight] columns split the leftover terminal width
 * (after the selection indicator, inter-column separators and the fixed/auto columns) in proportion
 * to their weight.
 *
 * @param reservedWidth Cells reserved before the first column (e.g. the selection indicator).
 */
internal fun <T> computeTableColumnWidths(
    items: List<T>,
    columns: List<TableColumn<T>>,
    showHeaders: Boolean,
    terminalWidth: Int,
    reservedWidth: Int,
): List<Int> {
    if (columns.isEmpty()) return emptyList()

    val resolved = IntArray(columns.size) { -1 }
    var weightTotal = 0f

    columns.forEachIndexed { index, column ->
        when (val width = column.width) {
            is TableColumnWidth.Fixed -> resolved[index] = width.chars.coerceAtLeast(0)
            is TableColumnWidth.Auto -> {
                val headerWidth = if (showHeaders) column.header.length else 0
                val contentWidth = if (items.isEmpty()) 0 else items.maxOf { column.valueOf(it).length }
                resolved[index] = maxOf(headerWidth, contentWidth)
            }
            is TableColumnWidth.Weight -> weightTotal += width.weight.coerceAtLeast(0f)
        }
    }

    if (weightTotal > 0f) {
        val separatorTotal = TABLE_COLUMN_SEPARATOR.length * (columns.size - 1).coerceAtLeast(0)
        val fixedSum = resolved.filter { it >= 0 }.sum()
        val available =
            (terminalWidth - reservedWidth - separatorTotal - fixedSum).coerceAtLeast(columns.size)
        columns.forEachIndexed { index, column ->
            val width = column.width
            if (width is TableColumnWidth.Weight) {
                val share = (available * (width.weight.coerceAtLeast(0f) / weightTotal)).toInt()
                resolved[index] = share.coerceAtLeast(1)
            }
        }
    }

    return resolved.toList()
}
