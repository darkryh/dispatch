package com.ead.dispatch.sample.presentation.library.locations

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
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.navigation.EntityEditorRoute
import com.ead.dispatch.sample.navigation.LocationListRoute
import com.ead.dispatch.sample.presentation.library.model.ListEntry
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.SelectableList
import com.ead.dispatch.widget.SelectableListStyles
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextOverflow
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun LocationListScreen(backStack: NavBackStack<NavKey>, route: LocationListRoute) {
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<LocationListViewModel>()
    val state by viewModel.state.collectAsState()

    var selectedIndex by remember { mutableStateOf(0) }
    val maxIndex = (state.items.size - 1).coerceAtLeast(0)
    if (selectedIndex > maxIndex) {
        selectedIndex = maxIndex
    }

    fun openSelected() {
        val entry = state.items.getOrNull(selectedIndex)
        when (entry) {
            is ListEntry.Create -> {
                backStack.navigate(EntityEditorRoute(type = "locations", storyId = route.storyId, entityId = null))
            }
            is ListEntry.Item -> {
                backStack.navigate(EntityEditorRoute(type = "locations", storyId = route.storyId, entityId = entry.data.id))
            }
            null -> Unit
        }
    }

    DisposableEffect(state.items.size) {
        val dispose = keyboardInterceptor.register { event ->
            when (event.key) {
                "Escape", "Esc" -> {
                    backStack.popBackStack()
                    true
                }
                "Enter" -> {
                    openSelected()
                    true
                }
                "ArrowUp" -> {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    true
                }
                "ArrowDown" -> {
                    selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
                    true
                }
                "n", "N" -> {
                    backStack.navigate(EntityEditorRoute(type = "locations", storyId = route.storyId, entityId = null))
                    true
                }
                else -> false
            }
        }
        onDispose { dispose() }
    }

    val titleStyle = rgb("#F6C177") + TextStyle(bold = true)
    val metaStyle = theme.muted

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(text = "Locations", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(text = "Loading locations...", style = theme.muted)
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
                SelectableList(
                    items = state.items,
                    selectedIndex = selectedIndex,
                    styles = SelectableListStyles(
                        prefix = theme.muted,
                        selectedPrefix = theme.accent + TextStyle(bold = true),
                    ),
                    itemSpacing = 1,
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
                                    style = metaStyle,
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
                                style = metaStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.tags,
                                style = metaStyle,
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
                Text(
                    text = "Arrow keys to navigate · Enter open · N new · Esc back",
                    style = theme.muted,
                )
            }
        }
    }
}
