# Widgets reference

Package: `io.github.darkryh.dispatch.widget`. Module: `dispatch-widgets`.

This page documents every public widget, grouped by purpose. Each entry gives the composable's
signature, its parameters, and a short example. Widgets accept a
[`Modifier`](modifiers.md) and render styled text through Mordant `TextStyle`s; sizes are in
terminal cells.

Many interactive widgets handle the keyboard themselves through the
[keyboard interceptor](input.md#keyboardinterceptor) and expose a `state` holder you can create with
a `remember…State()` helper to read or drive their selection from outside.

## Contents

- [Text](#text)
- [Buttons and selection](#buttons-and-selection) — `Button`, `IconButton`, `ButtonRow`,
  `ToggleButton`, `RadioButton`, `SegmentedButton`, `Modifier.clickable`
- [Text input](#text-input) — `TextField`, `PasswordField`, `BasicTextFieldRenderer`, `TextFieldState`
- [Lists](#lists) — `LazyColumn`, `ScrollableList`, `SelectableList`, `MultiSelectList`, `SelectMenu`
- [Tables and grids](#tables-and-grids) — `Table`, `FilterableTable`, `Grid`
- [Tree](#tree)
- [Command palette and selectors](#command-palette-and-selectors) — `CommandPalette`,
  `FilterableSelector`, `DecisionPrompt`
- [Checklists and tasks](#checklists-and-tasks) — `Checklist`, `TaskList`
- [Surfaces and structure](#surfaces-and-structure) — `Panel`, `Surface`, `Divider`, `Scaffold`,
  `Chip`, `KeyHintBar`
- [Progress](#progress) — `LinearProgressIndicator`, `Spinner`, `LoadingIndicator`
- [Diffs](#diffs) — `FileDiff`

---

## Text

### Text

```kotlin
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    align: TextAlign = TextAlign.LEFT,
    maxLines: Int? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    markdown: Boolean = false,
)
```

Displays styled text. Set `markdown = true` to render Markdown. `align` is a Mordant `TextAlign`
(`LEFT`, `RIGHT`, `CENTER`, `NONE`, `JUSTIFY`).

```kotlin
val theme = LocalTheme.current
Text("Ready.", style = theme.success)
Text("A long line that will be cut", maxLines = 1, overflow = TextOverflow.Ellipsis)
```

**`TextOverflow`** (enum): `Clip`, `Ellipsis`, `Visible`.

---

## Buttons and selection

### Button

```kotlin
@Composable
fun Button(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.Outlined,
)
```

A focusable, clickable button. Activates with Enter, Return, or Space when focused.

```kotlin
Button(text = "Save", onClick = { save() })
Button(text = "Delete", onClick = { delete() }, style = ButtonStyle.Filled)
```

**`ButtonStyle`** (enum): `Outlined` (`[ OK ]`), `Angled` (`<OK>`), `Text` (`OK`), `Filled`
(`▌OK▐`), `Rounded` (`(OK)`).

### IconButton

```kotlin
@Composable
fun IconButton(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = true,
)
```

A button showing a single glyph or emoji. Set `bordered = false` for a borderless icon.

### ButtonRow

```kotlin
@Composable
fun ButtonRow(
    modifier: Modifier = Modifier,
    spacing: Int = 2,
    content: @Composable () -> Unit,
)
```

Lays out buttons horizontally with `spacing` cells between them.

### ToggleButton

```kotlin
@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
    style: ToggleStyle = ToggleStyle.Checkbox,
)
```

A checkbox / toggle. **`ToggleStyle`** (enum, `checkedChar` / `uncheckedChar`): `Checkbox`
(`[✓]`/`[ ]`), `Square` (`[■]`/`[ ]`), `Circle` (`(●)`/`( )`), `Switch` (`[ON ]`/`[OFF]`), `Emoji`
(`✅`/`⬜`).

```kotlin
var on by remember { mutableStateOf(false) }
ToggleButton(checked = on, onCheckedChange = { on = it }, label = "Wrap lines")
```

### RadioButton

```kotlin
@Composable
fun RadioButton(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
)
```

A single-selection radio button. Render one per option and track the selected index yourself.

### SegmentedButton

```kotlin
@Composable
fun SegmentedButton(
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Press Enter",
    unfocusedFill: TextStyle = rgb("#2E3138"),
    focusedFill: TextStyle = rgb("#6BE3FF"),
    unfocusedTextStyle: TextStyle = rgb("#FFFFFF"),
    focusedTextStyle: TextStyle = rgb("#0B0F14"),
    placeholderStyle: TextStyle = rgb("#82858A"),
    paddingHorizontal: Int = 2,
    paddingVertical: Int = 1,
)
```

A focusable button that cycles through `options` each time it is activated.

### Modifier.clickable

```kotlin
@Composable
fun Modifier.clickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier
```

Makes any node focusable and activatable, invoking `onClick` on Enter, Return, or Space while
focused. Use it to make a custom composable behave like a button.

---

## Text input

### TextField

The everyday text input. It handles the keyboard for you — printable characters, Enter, Backspace,
and optional history navigation.

```kotlin
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
    cursorPosition: Int? = null,
    onCursorPositionChange: ((Int) -> Unit)? = null,
    historyItems: List<String> = emptyList(),
    historyIndexState: InputHistoryIndexState? = null,
    maskChar: Char? = null,
)
```

```kotlin
var text by remember { mutableStateOf("") }
TextField(
    value = text,
    onValueChange = { text = it },
    icon = "› ",
    placeholder = "Type a message…",
    onSubmit = { send(it) },
)
```

A `state`-driven overload is also available:

```kotlin
@Composable
fun TextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    onSubmit: ((String) -> Unit)? = null,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
    historyItems: List<String> = emptyList(),
    historyIndexState: InputHistoryIndexState? = null,
    maskChar: Char? = null,
)
```

### PasswordField

```kotlin
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "Password",
    enabled: Boolean = true,
    maskChar: Char = '•',
)
```

A `TextField` that masks each character with `maskChar`.

### BasicTextFieldRenderer

A pure display renderer with **no** keyboard handling — useful when you manage input yourself. Two
overloads, by value or by `TextFieldState`:

```kotlin
@Composable
fun BasicTextFieldRenderer(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    maxLines: Int? = null,
    cursorPosition: Int = value.length,
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
)

@Composable
fun BasicTextFieldRenderer(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    icon: String = "",
    placeholder: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    showCursor: Boolean = true,
    cursorChar: String = "█",
    textStyle: TextStyle? = null,
    placeholderStyle: TextStyle? = null,
    iconStyle: TextStyle? = null,
)
```

### TextFieldState

```kotlin
class TextFieldState(initialValue: String = "")
```

Holds an input's value, cursor, and selection.

- Properties: `value`, `cursorPosition`, `selectionStart`, `selectionEnd`, `hasFocus`.
- Methods: `insert(text)`, `deleteBackward()`, `deleteForward()`, `moveCursorLeft()`,
  `moveCursorRight()`, `moveCursorToStart()`, `moveCursorToEnd()`, `hasSelection()`,
  `clearSelection()`, `deleteSelection()`, `clear()`.

```kotlin
@Composable fun rememberTextFieldState(initialValue: String = ""): TextFieldState
```

### Input history

```kotlin
class InputHistoryIndexState   // properties: index (starts -1), draft (starts null)

@Composable fun rememberInputHistoryIndexState(): InputHistoryIndexState
```

Pass `historyItems` and a `rememberInputHistoryIndexState()` to `TextField` to enable Up/Down recall
of previous entries.

---

## Lists

### LazyColumn

```kotlin
@Composable
fun LazyColumn(
    modifier: Modifier = Modifier,
    state: ScrollState = rememberScrollState(),
    stickToEnd: Boolean = false,
    content: @Composable LazyListScope.() -> Unit,
)
```

A keyed, viewport-composed vertical list. Set `stickToEnd = true` to keep the newest item in view
(for logs or chat). Declare items in the `LazyListScope`:

```kotlin
interface LazyListScope {
    fun item(key: Any? = null, content: @Composable () -> Unit)
    fun <T> items(list: List<T>, key: ((T) -> Any)? = null, itemContent: @Composable (T) -> Unit)
}
```

```kotlin
LazyColumn(stickToEnd = true) {
    items(messages, key = { it.id }) { message ->
        Text(message.body)
    }
}
```

### ScrollableList

```kotlin
@Composable
fun <T> ScrollableList(
    items: List<T>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    itemContent: @Composable (T) -> Unit,
)

@Composable
fun <T> ScrollableListWithIndicator(
    items: List<T>,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    showScrollbar: Boolean = true,
    itemContent: @Composable (T) -> Unit,
)
```

A vertically scrollable list; the `WithIndicator` variant draws a scrollbar.

**`ScrollState`** — `class ScrollState(initialOffset: Int = 0)`, created with
`rememberScrollState(initialOffset: Int = 0)`.

- Properties: `offset`, `contentHeight`, `viewportHeight`, `maxOffset`, `canScrollUp`,
  `canScrollDown`.
- Methods: `scrollBy(delta)`, `scrollByPage(direction)`, `scrollTo(newOffset)`, `scrollToTop()`,
  `scrollToBottom()`, `scrollToItem(index, itemHeights)`.

### SelectableList

```kotlin
@Composable
fun <T> SelectableList(
    items: List<T>,
    selectedIndex: Int,
    styles: SelectableListStyles,
    modifier: Modifier = Modifier,
    itemSpacing: Int = 0,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
)
```

Renders a vertical list with a prefix indicator on the selected row. You own `selectedIndex`.

`SelectableWindowedList` renders only a window of rows around the selection:

```kotlin
@Composable
fun <T> SelectableWindowedList(
    items: List<T>,
    selectedIndex: Int,
    visibleCount: Int,
    styles: SelectableListStyles,
    modifier: Modifier = Modifier,
    itemSpacing: Int = 0,
    itemContent: @Composable (item: T, isSelected: Boolean) -> Unit,
)
```

**`SelectableListStyles`**:

```kotlin
data class SelectableListStyles(
    val prefix: TextStyle,
    val selectedPrefix: TextStyle,
    val prefixText: String = "  ",
    val selectedPrefixText: String = "> ",
)
```

The window math is also exposed for custom widgets:

```kotlin
data class WindowSlice<T>(val window: List<T>, val localSelected: Int, val startIndex: Int, val endIndex: Int)
fun <T> computeWindowSlice(items: List<T>, selectedIndex: Int, visibleCount: Int): WindowSlice<T>
```

### MultiSelectList

```kotlin
@Composable
fun <T, K> MultiSelectList(
    items: List<T>,
    key: (T) -> K,
    modifier: Modifier = Modifier,
    state: MultiSelectState<K> = rememberMultiSelectState(),
    checkedGlyph: String = "[x] ",
    uncheckedGlyph: String = "[ ] ",
    cursorIndicator: String = "> ",
    visibleCount: Int? = null,
    styles: SelectableListStyles = SelectableListStyles(prefix = TextStyle(), selectedPrefix = TextStyle()),
    onSelectionChange: (Set<K>) -> Unit = {},
    onConfirm: (Set<K>) -> Unit = {},
    itemContent: @Composable (item: T, checked: Boolean, focused: Boolean) -> Unit,
)
```

A keyboard-driven multi-select list: Up/Down move the cursor, Space toggles, Enter confirms.

**`MultiSelectState`** — `class MultiSelectState<K>(initialSelected: Set<K> = emptySet())`, created
with `rememberMultiSelectState`. Properties: `selectedKeys`, `cursorIndex`. Methods: `toggle(key)`,
`moveUp()`, `moveDown()`, `selectAll(keys)`, `clear()`.

### SelectMenu

```kotlin
@Composable
fun SelectMenu(
    items: List<MenuItem>,
    modifier: Modifier = Modifier,
    state: SelectMenuState = rememberSelectMenuState(),
    cursorIndicator: String = "> ",
    styles: SelectableListStyles = SelectableListStyles(prefix = TextStyle(), selectedPrefix = TextStyle()),
    visibleCount: Int? = null,
    itemStyle: TextStyle? = null,
    focusedStyle: TextStyle? = null,
    disabledStyle: TextStyle? = null,
)
```

A vertical action menu. Up/Down highlight a row; Enter or Return invokes its `onSelect`; disabled
rows are skipped.

```kotlin
data class MenuItem(val label: String, val enabled: Boolean = true, val onSelect: () -> Unit)

data class MenuEntry<T>(
    val value: T,
    val label: String,
    val enabled: Boolean = true,
    val onSelect: (T) -> Unit,
)   // MenuEntry<T>.toMenuItem(): MenuItem
```

**`SelectMenuState`** — `class SelectMenuState(initialIndex: Int = 0)`, created with
`rememberSelectMenuState`. Property: `cursorIndex`.

---

## Tables and grids

### Table

```kotlin
@Composable
fun <T> Table(
    items: List<T>,
    columns: List<TableColumn<T>>,
    modifier: Modifier = Modifier,
    showHeaders: Boolean = true,
    headerStyle: TextStyle? = rgb("#ffffff") + TextStyle(bold = true),
    rowStyle: TextStyle? = rgb("#ffffff"),
    maxVisibleRows: Int? = null,
)
```

A static, read-only data table.

```kotlin
data class TableColumn<T>(
    val header: String,
    val width: TableColumnWidth = TableColumnWidth.Auto,
    val align: TextAlign = TextAlign.LEFT,
    val valueOf: (T) -> String,
)

sealed interface TableColumnWidth {
    data object Auto
    data class Fixed(val chars: Int)
    data class Weight(val weight: Float)
}
```

```kotlin
Table(
    items = users,
    columns = listOf(
        TableColumn("Name", valueOf = { it.name }),
        TableColumn("Score", align = TextAlign.RIGHT, valueOf = { it.score.toString() }),
    ),
)
```

### FilterableTable

```kotlin
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
)
```

An interactive table with type-to-filter and keyboard navigation.

**`FilterableTableState`** — `class FilterableTableState<T>(initialVisible: Boolean = true,
initialSelectedIndex: Int = 0)`, created with `rememberTableState`. Properties: `isVisible`,
`selectedIndex`, `filterText`, `filteredItems`, `selectedItem`, `hasResults`. Methods: `moveUp()`,
`moveDown()`, `appendFilter(char)`, `removeLastFilter()`, `clearFilter()`.

### Grid

```kotlin
@Composable
fun <T> Grid(
    items: List<T>,
    modifier: Modifier = Modifier,
    cells: GridCells = GridCells.Adaptive(minSize = 18),
    gap: Int = 2,
    leftPadding: Int = 2,
    enforceCellWidth: Boolean = true,
    content: @Composable (item: T, cellWidth: Int) -> Unit,
)

sealed class GridCells {
    data class Fixed(val count: Int)
    data class Adaptive(val minSize: Int)
}
```

An adaptive grid. `GridCells.Fixed` uses a set column count; `GridCells.Adaptive` fits as many
columns of at least `minSize` cells as the width allows.

---

## Tree

```kotlin
@Composable
fun <T> Tree(
    roots: List<TreeNode<T>>,
    modifier: Modifier = Modifier,
    nodeKey: (T) -> Any = { requireNotNull(it) { "Tree node key must be non-null" } },
    indentPerLevel: Int = 2,
    expandedGlyph: String = "▾ ",
    collapsedGlyph: String = "▸ ",
    leafGlyph: String = "  ",
    selectionIndicator: String = "> ",
    visibleCount: Int? = null,
    enabled: Boolean = true,
    state: TreeState = rememberTreeState(),
    nodeContent: @Composable (T, depth: Int, expanded: Boolean) -> Unit,
)

data class TreeNode<T>(val value: T, val children: List<TreeNode<T>> = emptyList())
```

An expandable/collapsible tree. Up/Down move the selection; Left/Right (or Enter) collapse/expand.

**`TreeState`** — `class TreeState(initialExpandedKeys: Set<Any> = emptySet(),
initialSelectedIndex: Int = 0)`, created with `rememberTreeState`. Properties: `expandedKeys`,
`selectedIndex`. Methods: `isExpanded(key)`, `expand(key)`, `collapse(key)`, `toggle(key)`,
`moveUp()`, `moveDown(lastIndex)`.

---

## Command palette and selectors

### CommandPalette

```kotlin
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
)

data class CommandOption<T>(
    val label: String,
    val description: String = "",
    val icon: String? = null,
    val data: T,
    val enabled: Boolean = true,
)
```

A filterable command list that pops up when the input begins with `triggerChar`. Navigate with
Up/Down and confirm with Enter.

**`CommandPaletteState`** — created with `rememberCommandPaletteState<T>(initialVisible: Boolean =
false, initialSelectedIndex: Int = 0)`. Properties: `isVisible`, `selectedIndex`, `filterText`,
`filteredOptions`, `selectedOption`, `hasResults`.

### FilterableSelector

A reusable selection engine plus key-binding helpers, used to build pop-up pickers.

```kotlin
class FilterableSelectorState<T>(
    initialVisible: Boolean,
    initialSelectedIndex: Int,
    initialFilterText: String = "",
)

@Composable
fun <T> rememberSelectorState(
    initialVisible: Boolean = true,
    initialSelectedIndex: Int = 0,
    initialFilterText: String = "",
): FilterableSelectorState<T>
```

`FilterableSelectorState` properties: `isVisible`, `selectedIndex`, `filterText`, `filteredOptions`,
`selectedOption`, `hasResults`. Methods: `updateFilteredOptions(options)`,
`ensureSelectionInBounds()`, `resetSelection()`, `moveUp()`, `moveDown()`, `appendFilter(char)`,
`removeLastFilter()`, `clearFilter()`.

Drive it from key events with:

```kotlin
data class SelectorKeyBindings(
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onConfirm: (() -> Boolean)? = null,
    val onCancel: (() -> Boolean)? = null,
    val onTab: (() -> Boolean)? = null,
    val onBackspace: (() -> Boolean)? = null,
    val onCharacter: ((Char) -> Boolean)? = null,
    val isCharacterEvent: (KeyboardEvent) -> Boolean = { event -> event.asKeyEvent().isText },
)

fun handleSelectorKeyEvent(event: KeyboardEvent, bindings: SelectorKeyBindings): Boolean
```

### DecisionPrompt

```kotlin
@Composable
fun DecisionPrompt(
    question: String,
    options: List<DecisionOption>,
    onSubmit: (DecisionSelection) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "type a custom response…",
    visible: Boolean = true,
    enabled: Boolean = true,
    selectionIndicator: String = "> ",
    unselectedIndicator: String = "  ",
    state: DecisionPromptState = rememberDecisionPromptState(),
    textStyles: DecisionPromptTextStyles = DecisionPromptTextStyles(),
)

data class DecisionOption(val label: String)

sealed interface DecisionSelection {
    data class Option(val option: DecisionOption)
    data class Custom(val text: String)
}
```

Asks a question with selectable options plus a free-text fallback. `onSubmit` receives either a
chosen `Option` or a typed `Custom` answer.

**`DecisionPromptState`** — created with `rememberDecisionPromptState(initialSelectedIndex: Int = 0,
initialCustomText: String = "")`. Properties: `selectedIndex`, `customText`.

---

## Checklists and tasks

### Checklist

```kotlin
@Composable
fun Checklist(
    items: List<ChecklistItem>,
    modifier: Modifier = Modifier,
    selectedIndex: Int = -1,
    style: ChecklistStyle = ChecklistStyle.Checkbox,
    textStyles: ChecklistTextStyles = ChecklistTextStyles(),
)

data class ChecklistItem(val name: String, val checked: Boolean = false, val enabled: Boolean = true)
```

A read-model checklist. **`ChecklistStyle`** (enum): `Checkbox`, `Square`, `Circle`, `Emoji`.

### TaskList

A list that tracks execution progress, with a spinner on the in-progress row. Two overloads — by
`List<String>` or by a model with projection lambdas:

```kotlin
@Composable
fun TaskList(
    tasks: List<String>,
    modifier: Modifier = Modifier,
    spinnerFrame: Int = 0,
    showStatusIcons: Boolean = true,
    status: List<TaskStatus> = emptyList(),
    details: List<String?> = emptyList(),
    textStyles: TaskListTextStyles = TaskListTextStyles(),
)

@Composable
fun <T> TaskList(
    tasks: List<T>,
    modifier: Modifier = Modifier,
    spinnerFrame: Int = 0,
    showStatusIcons: Boolean = true,
    status: (T) -> TaskStatus = { TaskStatus.Pending },
    description: (T) -> String = { it.toString() },
    details: (T) -> String? = { null },
    textStyles: TaskListTextStyles = TaskListTextStyles(),
)
```

**`TaskStatus`** (enum): `Pending`, `InProgress`, `Completed`, `Failed`, `Skipped`. Advance
`spinnerFrame` over time to animate the in-progress spinner.

---

## Surfaces and structure

### Panel

```kotlin
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    borderStyle: BorderStyle = BorderStyle.Rounded,
    titleStyle: TextStyle? = null,
    expand: Boolean = false,
    content: @Composable () -> Unit,
)
```

A bordered panel with an optional title in the top border. `borderStyle` is a
[`BorderStyle`](modifiers.md#border): `None`, `Ascii`, `Rounded`, `Square`, `Heavy`, `Double`,
`Dashed`. Set `expand = true` to fill the available width.

```kotlin
Panel(title = "Logs", borderStyle = BorderStyle.Square) {
    Text(logText)
}
```

### Surface

```kotlin
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    style: SurfaceStyle = SurfaceStyle.None,
    content: @Composable () -> Unit,
)

data class SurfaceStyle(
    val topRule: SurfaceRule? = null,
    val bottomRule: SurfaceRule? = null,
    val fill: TextStyle? = null,
    val box: SurfaceBox? = null,
)
```

A container that can add a background fill, padding, and top/bottom rules. Build a style with the
companion helpers:

```kotlin
SurfaceStyle.None
SurfaceStyle.lines(char: Char = '─', style: TextStyle? = null)
SurfaceStyle.fill(fill: TextStyle, paddingHorizontal: Int = 1, paddingVertical: Int = 1)
```

```kotlin
Surface(style = SurfaceStyle.fill(rgb("#303846"))) {
    Text("Filled background with padding")
}

data class SurfaceRule(val char: Char = '─', val style: TextStyle? = null)
data class SurfaceBox(val paddingHorizontal: Int = 1, val paddingVertical: Int = 0)
```

### Divider

```kotlin
@Composable fun HorizontalDivider(modifier: Modifier = Modifier, char: Char = '─')
@Composable fun VerticalDivider(modifier: Modifier = Modifier, char: Char = '│')

@Composable fun HorizontalDivider(style: DividerStyle, modifier: Modifier = Modifier)
@Composable fun VerticalDivider(style: DividerStyle, modifier: Modifier = Modifier)
```

**`DividerStyle`** (enum, `horizontal` / `vertical` chars): `Light` (`─`/`│`), `Heavy` (`━`/`┃`),
`Double` (`═`/`║`), `Dashed` (`┄`/`┆`), `Dotted` (`·`/`·`), `Space` (` `/` `).

### Scaffold

```kotlin
@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    hints: List<KeyHint> = emptyList(),
    onBack: (() -> Unit)? = null,
    escGoesBack: Boolean = true,
    content: @Composable () -> Unit,
)
```

A screen frame with a header, body, footer, and optional Escape-to-go-back. Pass `hints` to render a
key-hint bar in the footer. For layout-only framing, see
[`TerminalScreen`](layout.md#terminalscreen).

### Chip

```kotlin
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
    paddingHorizontal: Int = 1,
)

@Composable
fun ChipRow(
    tags: List<String>,
    modifier: Modifier = Modifier,
    gap: Int = 1,
    fill: TextStyle = rgb("#2E3138"),
    textStyle: TextStyle = rgb("#E6EAF0"),
)
```

Small filled tags; `ChipRow` lays a list of strings out horizontally.

### KeyHintBar

```kotlin
@Composable
fun KeyHintBar(
    hints: List<KeyHint>,
    modifier: Modifier = Modifier,
    leftPadding: Int = 2,
    keyStyle: TextStyle = rgb("#A7B2BF"),
    descriptionStyle: TextStyle = rgb("#8A95A5"),
    separator: String = "·",
    separatorStyle: TextStyle = rgb("#6E7681"),
)

data class KeyHint(val key: String, val description: String)
```

A footer bar of key/description hints.

```kotlin
KeyHintBar(listOf(KeyHint("↑↓", "move"), KeyHint("Enter", "select"), KeyHint("Esc", "back")))
```

---

## Progress

### LinearProgressIndicator

```kotlin
@Composable
fun LinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    style: LinearProgressIndicatorStyle = LinearProgressIndicatorStyle.Blocks,
    showPercentage: Boolean = false,
)
```

A horizontal bar; `progress` is `0f`–`1f`. **`LinearProgressIndicatorStyle`** (enum): `Blocks`,
`Ascii`, `Dots`, `Arrows`, `Line`, `Minimal`.

### Spinner

```kotlin
@Composable
fun Spinner(
    frame: Int,
    modifier: Modifier = Modifier,
    style: SpinnerStyle = SpinnerStyle.Dots,
    textStyle: TextStyle? = null,
)
```

An indeterminate spinner. Advance `frame` each tick to animate. **`SpinnerStyle`** (enum): `Dots`,
`Circle`, `Growing`, `Pulse`, `Quadrants`, `Shades`.

```kotlin
var frame by remember { mutableStateOf(0) }
LaunchedEffect(Unit) { while (true) { delay(80); frame++ } }
Spinner(frame)
```

### LoadingIndicator

```kotlin
@Composable
fun LoadingIndicator(
    frame: Int,
    modifier: Modifier = Modifier,
    text: String = "Loading...",
    style: SpinnerStyle = SpinnerStyle.Dots,
)
```

A spinner paired with a label.

---

## Diffs

### FileDiff

```kotlin
@Composable
fun FileDiff(
    before: String,
    after: String,
    modifier: Modifier = Modifier,
    path: String? = null,
    focus: DiffFocus = DiffFocus.Changes,
    contextLines: Int = 3,
    showHeader: Boolean = true,
    showStats: Boolean = true,
    colors: DiffColors = DiffColors(),
    maxVisibleRows: Int? = null,
    pending: Set<Int> = emptySet(),
    pendingColor: TextStyle = DEFAULT_PENDING_COLOR,
)
```

Renders a line-based diff between `before` and `after` with a diff-style gutter.

**`DiffFocus`** (enum): `Changes`, `Additions`, `Deletions`, `Full`.

```kotlin
data class DiffColors(
    val added: TextStyle = rgb("#1E4D31"),
    val deleted: TextStyle = rgb("#5A2323"),
    val gutter: TextStyle = rgb("#6E7681"),
    val header: TextStyle = rgb("#E6EAF0") + TextStyle(bold = true),
    val stats: TextStyle = rgb("#8A95A5"),
    val content: TextStyle = rgb("#E6EAF0"),
)   // companion: DiffColors.Default
```

To compute diff data yourself (for paging or custom rendering), use `rememberFileDiff`, which
returns a `FileDiffResult`:

```kotlin
@Composable
fun rememberFileDiff(
    before: String,
    after: String,
    focus: DiffFocus = DiffFocus.Changes,
    contextLines: Int = 3,
): FileDiffResult
```

`FileDiffResult` exposes `stats: DiffStats` and `rows: List<DiffRow>`, plus
`page(index: Int, size: Int): FileDiffPage`. `FileDiffResult` and `DiffRow` are read-only — you
obtain them from `rememberFileDiff`, you do not construct them.

```kotlin
data class DiffStats(val totalLines: Int, val addedLines: Int, val deletedLines: Int, val modifiedLines: Int)
data class FileDiffPage(val rows: List<DiffRow>, val pageCount: Int, val pageIndex: Int)
```

## See also

- [Modifiers](modifiers.md) — size, padding, border, and focus.
- [Layout](layout.md) — arrange widgets with `Column`, `Row`, and `Box`.
- [Keyboard input](input.md) — drive interactive widgets and add your own shortcuts.
- [Theme](theme.md) — the `TextStyle`s widgets render with.
