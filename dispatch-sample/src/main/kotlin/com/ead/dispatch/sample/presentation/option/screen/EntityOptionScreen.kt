package com.ead.dispatch.sample.presentation.option.screen

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
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.navigation.CharacterRoute
import com.ead.dispatch.sample.navigation.EntityOptionRoute
import com.ead.dispatch.sample.presentation.components.ListOption
import com.ead.dispatch.sample.presentation.components.ListSelector
import com.ead.dispatch.sample.presentation.components.ListSelectorTextStyles
import com.ead.dispatch.sample.presentation.option.viewmodel.EntityOptionViewModel
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

/**
 * Reusable screen for listing entity options based on [EntityOptionRoute.type].
 *
 * This screen is invoked from multiple command routes (e.g. `/characters`, `/locations`)
 * and adapts its title, data source, and actions based on the provided route.
 */
@Dispatchable
fun EntityOptionScreen(backStack: NavBackStack<NavKey>, route: EntityOptionRoute) {

    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<EntityOptionViewModel>()

    val state by viewModel.state.collectAsState()
    val entityType = state.type ?: EntityOptionType.fromId(route.type)
    val title = entityType?.title ?: "Entities"

    var selectedIndex by remember { mutableStateOf(0) }

    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        val consumed = when (event.key) {
            "Escape", "Esc" -> {
                backStack.popBackStack()
                true
            }
            "Enter" -> {
                val selected = state.items.getOrNull(selectedIndex)
                if (entityType == EntityOptionType.CHARACTERS && selected != null) {
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
                if (entityType == EntityOptionType.CHARACTERS) {
                    backStack.navigate(CharacterRoute(storyId = route.storyId, characterId = null))
                    true
                } else {
                    false
                }
            }
            else -> false
        }
        return consumed
    }

    DisposableEffect(Unit) {
        val interceptorDispose = keyboardInterceptor.register { event ->
            handleKeyEvent(event)
        }

        onDispose {
            interceptorDispose()
        }
    }

    val selectorOptions = state.items.mapIndexed { index, item ->
        ListOption(
            id = "${entityType?.id ?: "entity"}-$index",
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
                    text = if (entityType == EntityOptionType.CHARACTERS) {
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
