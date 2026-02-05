package com.ead.dispatch.sample.presentation.library.cultures

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.NavBackStack
import com.ead.dispatch.navigation.NavKey
import com.ead.dispatch.navigation.navigate
import com.ead.dispatch.navigation.popBackStack
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.CultureListRoute
import com.ead.dispatch.sample.navigation.CultureEditorRoute
import com.ead.dispatch.sample.presentation.library.calculateVisibleCount
import com.ead.dispatch.sample.presentation.library.filterEntries
import com.ead.dispatch.sample.presentation.library.matchesQuery
import com.ead.dispatch.sample.presentation.library.defaultSelectionIndex
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.widget.FilterBarCard
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.SelectableWindowedList
import com.ead.dispatch.widget.SelectableListStyles
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextOverflow
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.rememberTextFieldState
import com.ead.dispatch.widget.rememberFilterInputController
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun CultureListScreen(
    backStack: NavBackStack<NavKey>,
    route: CultureListRoute
) {
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<CultureListViewModel>()
    val state by viewModel.state.collectAsState()
    val filterField = rememberTextFieldState()
    val filterController = rememberFilterInputController(filterField)
    val filterQuery = filterField.value
    val filteredEntries = remember(state.items, filterQuery) {
        filterEntries(state.items, filterQuery) { item, query ->
            matchesQuery(query, item.name, item.description)
        }
    }

    var selectedIndex by remember { mutableStateOf(0) }
    val maxIndex = (filteredEntries.size - 1).coerceAtLeast(0)
    if (selectedIndex > maxIndex) {
        selectedIndex = maxIndex
    }
    val resetKey = filterQuery to filteredEntries.size
    val lastResetKey = remember { mutableStateOf<Any?>(null) }
    if (lastResetKey.value != resetKey) {
        lastResetKey.value = resetKey
        selectedIndex = defaultSelectionIndex(filteredEntries, filterQuery)
    }
    fun openSelected() {
        val entry = filteredEntries.getOrNull(selectedIndex)
        when (entry) {
            is ListEntry.Create -> {
                backStack.navigate(CultureEditorRoute(storyId = route.storyId, cultureId = null))
            }
            is ListEntry.Item -> {
                backStack.navigate(CultureEditorRoute(storyId = route.storyId, cultureId = entry.data.id))
            }
            null -> Unit
        }
    }

    DisposableEffect(listOf(filteredEntries.size)) {
        val dispose = keyboardInterceptor.register(priority = 1) { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    backStack.popBackStack()
                    true
                }
                "Enter" -> {
                    openSelected()
                    true
                }
                "ArrowUp", "Up" -> {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    true
                }
                "ArrowDown", "Down" -> {
                    selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
                    true
                }
                "n", "N" -> {
                    if (event.ctrl) {
                        backStack.navigate(CultureEditorRoute(storyId = route.storyId, cultureId = null))
                        true
                    } else {
                        false
                    }
                }
                else -> {
                    if (filterController.handleKeyEvent(event)) {
                        true
                    } else {
                        false
                    }
                }
            }
        }
        onDispose { dispose() }
    }

    val titleStyle = rgb("#8FE388") + TextStyle(bold = true)
    val metaStyle = theme.muted
    val selectedMetaStyle = theme.primary + TextStyle(bold = true)
    val terminalHeight = LocalTerminalHeight.current
    val itemLines = 2
    val itemSpacing = 1
    val visibleCount = calculateVisibleCount(
        terminalHeight = terminalHeight,
        reservedLines = 7,
        itemLines = itemLines,
        itemSpacing = itemSpacing,
    )

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(text = "Cultures", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }
        item {
            FilterBarCard(
                state = filterField,
                placeholder = "Filter by name, traits, or region...",
                textStyle = theme.primary,
                placeholderStyle = theme.primary + TextStyle(dim = true),
            )
        }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(text = "Loading cultures...", style = theme.muted)
                }
            }
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(text = "Error: $error", style = theme.muted)
                }
            }
            return@LazyColumn
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                SelectableWindowedList(
                    items = filteredEntries,
                    selectedIndex = selectedIndex,
                    visibleCount = visibleCount,
                    styles = SelectableListStyles(
                        prefix = theme.muted,
                        selectedPrefix = theme.accent + TextStyle(bold = true),
                    ),
                    itemSpacing = itemSpacing,
                ) { entry, isSelected ->
                    when (entry) {
                        is ListEntry.Create -> {
                            Text(
                                text = entry.title,
                                style = if (isSelected) theme.accent + TextStyle(bold = true) else theme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            entry.subtitle?.let { subtitle ->
                                Text(
                                    text = subtitle,
                                    style = if (isSelected) selectedMetaStyle else metaStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        is ListEntry.Item -> {
                            val item = entry.data
                            Text(
                                text = item.name,
                                style = if (isSelected) titleStyle else theme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.description,
                                style = if (isSelected) selectedMetaStyle else metaStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                KeyHintBar(
                    hints = listOf(
                        KeyHint("Arrow", "navigate"),
                        KeyHint("Enter", "open"),
                        KeyHint("Ctrl+N", "new"),
                        KeyHint("Esc", "back"),
                        KeyHint("Type", "filter"),
                    ),
                    keyStyle = theme.muted,
                    descriptionStyle = theme.muted,
                    separatorStyle = theme.muted,
                )
            }
        }
    }
}