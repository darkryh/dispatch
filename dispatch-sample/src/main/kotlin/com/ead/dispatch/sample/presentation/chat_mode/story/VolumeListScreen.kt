package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTerminalHeight
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.ChapterListRoute
import com.ead.dispatch.sample.navigation.VolumeListRoute
import com.ead.dispatch.sample.presentation.library.calculateVisibleCount
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.SelectableListStyles
import com.ead.dispatch.widget.SelectableWindowedList
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextOverflow
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun VolumeListScreen(route: VolumeListRoute) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val terminalHeight = LocalTerminalHeight.current
    val viewModel = viewModel<VolumeListViewModel>()
    val state by viewModel.state.collectAsState()
    val entries = state.items

    var selectedIndex by remember { mutableStateOf(0) }
    val maxIndex = (entries.size - 1).coerceAtLeast(0)
    if (selectedIndex > maxIndex) {
        selectedIndex = maxIndex
    }

    fun openSelected() {
        val entry = entries.getOrNull(selectedIndex) as? ListEntry.Item ?: return
        navigator.navigate(
            ChapterListRoute(
                storyId = route.storyId,
                volumeId = entry.data.id,
            )
        )
    }

    DisposableEffect(entries.size) {
        val dispose = keyboardInterceptor.register(priority = 1) { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    navigator.popBackStack()
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
                else -> false
            }
        }
        onDispose { dispose() }
    }

    val visibleCount = calculateVisibleCount(
        terminalHeight = terminalHeight,
        reservedLines = 7,
        itemLines = 3,
        itemSpacing = 1,
    )
    val titleStyle = rgb("#86D39E") + TextStyle(bold = true)
    val metaStyle = theme.muted
    val selectedMetaStyle = theme.primary + TextStyle(bold = true)

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text("Volumes", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text("Loading volumes...", style = theme.muted)
                }
            }
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text("Error: $error", style = theme.muted)
                }
            }
            return@LazyColumn
        }

        if (entries.isEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text("(none)", style = theme.muted)
                }
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    SelectableWindowedList(
                        items = entries,
                        selectedIndex = selectedIndex,
                        visibleCount = visibleCount,
                        styles = SelectableListStyles(
                            prefix = theme.muted,
                            selectedPrefix = theme.accent + TextStyle(bold = true),
                        ),
                        itemSpacing = 1,
                    ) { entry, isSelected ->
                        val item = (entry as ListEntry.Item).data
                        Text(
                            text = item.title,
                            style = if (isSelected) titleStyle else theme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.summary,
                            style = if (isSelected) selectedMetaStyle else metaStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.meta,
                            style = if (isSelected) selectedMetaStyle else metaStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        KeyHint("Enter", "chapters"),
                        KeyHint("Esc", "back"),
                    ),
                    keyStyle = theme.muted,
                    descriptionStyle = theme.muted,
                    separatorStyle = theme.muted,
                )
            }
        }
    }
}
