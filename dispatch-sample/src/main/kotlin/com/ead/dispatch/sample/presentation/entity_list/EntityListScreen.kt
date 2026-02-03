package com.ead.dispatch.sample.presentation.entity_list

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
import com.ead.dispatch.runtime.dispatchScope
import com.ead.dispatch.sample.navigation.CharacterRoute
import com.ead.dispatch.sample.navigation.EntityListRoute
import com.ead.dispatch.sample.presentation.components.ListOption
import com.ead.dispatch.sample.presentation.components.ListSelector
import com.ead.dispatch.sample.presentation.components.ListSelectorTextStyles
import com.ead.dispatch.state.getValue
import com.ead.dispatch.state.mutableStateOf
import com.ead.dispatch.state.remember
import com.ead.dispatch.state.setValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.Text
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun EntityListScreen(backStack: NavBackStack<NavKey>, route: EntityListRoute) {
    val theme = LocalTheme.current
    val scope = dispatchScope()
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<EntityListViewModel>()
    val state by viewModel.state.collectAsState()
    val type = route.type.lowercase()
    val title = when (type) {
        "characters" -> "Characters"
        "locations" -> "Locations"
        "arcs" -> "Arcs"
        "world-rules" -> "World Rules"
        "cultures" -> "Cultures"
        "events" -> "Events"
        "organizations" -> "Organizations"
        "relationships" -> "Relationships"
        "location-features" -> "Location Features"
        "artifacts" -> "Artifacts"
        "timeline" -> "Timeline Entries"
        "volumes" -> "Volumes"
        "chapters" -> "Chapters"
        "scenes" -> "Scenes"
        else -> "Entities"
    }

    var selectedIndex by remember { mutableStateOf(0) }

    val lastHandledEvent = remember { mutableStateOf<KeyboardEvent?>(null) }

    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        if (lastHandledEvent.value === event) {
            return true
        }
        val consumed = when (event.key) {
            "Escape", "Esc" -> {
                backStack.popBackStack()
                true
            }
            "Enter" -> {
                val selected = state.items.getOrNull(selectedIndex)
                if (type == "characters" && selected != null) {
                    val characterId = if (selected.isCreate) null else selected.id
                    backStack.navigate(CharacterRoute(storyId = route.storyId, characterId = characterId))
                    true
                } else {
                    false
                }
            }
            "ArrowUp" -> {
                selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                true
            }
            "ArrowDown" -> {
                val maxIndex = (state.items.size - 1).coerceAtLeast(0)
                selectedIndex = (selectedIndex + 1).coerceAtMost(maxIndex)
                true
            }
            "n", "N" -> {
                if (type == "characters") {
                    backStack.navigate(CharacterRoute(storyId = route.storyId, characterId = null))
                    true
                } else {
                    false
                }
            }
            else -> false
        }
        if (consumed) {
            lastHandledEvent.value = event
        }
        return consumed
    }

    DisposableEffect(Unit) {
        val interceptorDispose = keyboardInterceptor.register { event ->
            handleKeyEvent(event)
        }

        val dispose = scope.addKeyEventHandler { event ->
            handleKeyEvent(event)
        }

        onDispose {
            dispose()
            interceptorDispose()
        }
    }

    val selectorOptions = state.items.mapIndexed { index, item ->
        ListOption(
            id = "$type-$index",
            title = item.title,
            description = "· ${item.subtitle}",
            data = item,
        )
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = title,
                    style = theme.primary + TextStyle(bold = true),
                )
            }
        }
        item { Spacer(Modifier.height(1)) }
        if (state.isLoading) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(
                        text = "Loading ${title.lowercase()}...",
                        style = theme.muted,
                    )
                }
            }
        } else if (state.error != null) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(
                        text = "Error: ${state.error}",
                        style = theme.muted,
                    )
                }
            }
        } else if (selectorOptions.isEmpty()) {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    Text(
                        text = "No items available.",
                        style = theme.muted,
                    )
                }
            }
        } else {
            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(2))
                    ListSelector(
                        options = selectorOptions,
                        selectedIndex = selectedIndex,
                        textStyles = ListSelectorTextStyles(
                            prefix = theme.muted,
                            selectedPrefix = theme.accent + TextStyle(bold = true),
                            title = theme.primary,
                            selectedTitle = theme.accent + TextStyle(bold = true),
                            description = theme.muted,
                            selectedDescription = theme.muted,
                        )
                    )
                }
            }
        }
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = if (type == "characters") {
                        "Arrow keys to navigate · Enter open · N new · Esc back"
                    } else {
                        "Arrow keys to navigate · Esc to go back"
                    },
                    style = theme.muted,
                )
            }
        }
    }
}
