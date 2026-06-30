@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.lists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.weight
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.ControlPanel
import io.github.darkryh.dispatch.sample.designsystem.ControlSpec
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundScaffold
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.KeyHint
import io.github.darkryh.dispatch.widget.LazyColumn
import io.github.darkryh.dispatch.widget.MenuItem
import io.github.darkryh.dispatch.widget.MultiSelectList
import io.github.darkryh.dispatch.widget.ScrollableListWithIndicator
import io.github.darkryh.dispatch.widget.SelectMenu
import io.github.darkryh.dispatch.widget.SelectableList
import io.github.darkryh.dispatch.widget.SelectableListStyles
import io.github.darkryh.dispatch.widget.SelectableWindowedList
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.rememberScrollState
import io.github.darkryh.dispatch.widget.rememberSelectMenuState

/**
 * Lists playground.
 *
 * The list widgets here own the keyboard themselves, so there is no playground controller: **Tab**
 * moves focus to the `SelectMenu` / `MultiSelectList`, then **↑/↓** moves within the focused widget,
 * **Space** checks a row and **Enter** activates a menu item. The four display variants on the left
 * ([SelectableList], [SelectableWindowedList], [ScrollableListWithIndicator], [LazyColumn]) are shown
 * compactly; the two interactive widgets report back into the controls echo.
 */
@Composable
fun ListsScreen(viewModel: ListsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundScaffold(
        title = "Lists",
        subtitle = "Tab focuses the menu / checklist, then ↑/↓ move · Space checks · Enter selects.",
        showControlHints = false,
        hints =
            listOf(
                KeyHint("Tab", "focus"),
                KeyHint("↑/↓", "move"),
                KeyHint("Space", "check"),
                KeyHint("Enter", "select"),
            ),
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Value("Menu pick", state.menuChoice),
                        ControlSpec.Value("Checked", state.checkedCount.toString()),
                    ),
                selected = -1,
            )
        },
        preview = {
            ListsPreview(
                onPick = { viewModel.sendIntent(ListsIntent.MenuPicked(it)) },
                onMultiChanged = { viewModel.sendIntent(ListsIntent.MultiChanged(it)) },
            )
        },
    )
}

@Composable
private fun ListsPreview(
    onPick: (String) -> Unit,
    onMultiChanged: (Int) -> Unit,
) {
    val theme = LocalTheme.current
    val listStyles =
        SelectableListStyles(
            prefix = theme.muted,
            selectedPrefix = theme.accent,
        )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Four display-only variants, laid out 2×2 to stay compact.
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("SelectableList", style = theme.muted)
                SelectableList(items = SAMPLE_ITEMS, selectedIndex = 0, styles = listStyles) { item, selected ->
                    Text(item, style = if (selected) theme.accent else theme.primary)
                }
                Spacer(Modifier.height(1))
                Text("Windowed (3)", style = theme.muted)
                SelectableWindowedList(
                    items = SAMPLE_ITEMS,
                    selectedIndex = 0,
                    visibleCount = 3,
                    styles = listStyles,
                ) { item, selected ->
                    Text(item, style = if (selected) theme.accent else theme.primary)
                }
            }
            Spacer(Modifier.width(2))
            Column(modifier = Modifier.weight(1f)) {
                Text("Scrollable", style = theme.muted)
                ScrollableListWithIndicator(
                    items = SAMPLE_ITEMS,
                    modifier = Modifier.height(3),
                    scrollState = rememberScrollState(),
                ) { item ->
                    Text(item)
                }
                Spacer(Modifier.height(1))
                Text("LazyColumn", style = theme.muted)
                LazyColumn(modifier = Modifier.height(3)) {
                    items(SAMPLE_ITEMS) { Text("• $it") }
                }
            }
        }
        Spacer(Modifier.height(1))

        // Two interactive, Tab-focusable widgets.
        Text("SelectMenu — Tab to focus · ↑/↓ · Enter", style = theme.muted)
        SelectMenu(
            items =
                listOf(
                    MenuItem(label = "New message", onSelect = { onPick("New message") }),
                    MenuItem(label = "Reply", onSelect = { onPick("Reply") }),
                    MenuItem(label = "Forward", onSelect = { onPick("Forward") }),
                    MenuItem(label = "Delete", onSelect = { onPick("Delete") }),
                ),
            state = rememberSelectMenuState(),
            styles = listStyles,
            focusedStyle = theme.accent,
            itemStyle = theme.primary,
        )
        Spacer(Modifier.height(1))

        Text("MultiSelectList — Tab to focus · Space checks", style = theme.muted)
        MultiSelectList(
            items = SAMPLE_ITEMS.take(4),
            key = { it },
            styles = listStyles,
            visibleCount = 4,
            onSelectionChange = { onMultiChanged(it.size) },
        ) { item, checked, focused ->
            Text(
                item,
                style =
                    if (focused) {
                        theme.accent
                    } else if (checked) {
                        theme.info
                    } else {
                        theme.primary
                    },
            )
        }
    }
}
