package com.ead.dispatch.sample.presentation.chat_mode.story

import com.ead.dispatch.annotation.Dispatchable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.runtime.DisposableEffect
import com.ead.dispatch.runtime.LocalKeyboardInterceptor
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.navigation.ArcListRoute
import com.ead.dispatch.sample.navigation.ChapterListRoute
import com.ead.dispatch.sample.navigation.CharacterListRoute
import com.ead.dispatch.sample.navigation.LocationListRoute
import com.ead.dispatch.sample.navigation.SceneListRoute
import com.ead.dispatch.sample.navigation.StoryChatRoute
import com.ead.dispatch.sample.navigation.VolumeListRoute
import com.ead.dispatch.sample.navigation.WorldRuleListRoute
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.DividerStyle
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.CountTile
import com.ead.dispatch.widget.CountTileGrid
import com.ead.dispatch.widget.GridCells
import com.ead.dispatch.widget.EmptyState
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.KeyHint
import com.ead.dispatch.widget.KeyHintBar
import com.ead.dispatch.widget.TextOverflow
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle
import com.ead.dispatch.navigation.LocalNavigator
import com.ead.dispatch.theme.DispatchTheme

@Dispatchable
fun StoryChatScreen(route: StoryChatRoute) {
    val navigator = LocalNavigator.current
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<StoryChatViewModel>()
    val state by viewModel.state.collectAsState()

    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        val consumed = when (event.key.lowercase()) {
            "escape", "esc" -> {
                navigator.popBackStack()
                true
            }
            "c" -> {
                navigator.navigate(CharacterListRoute(storyId = route.storyId))
                true
            }
            "l" -> {
                navigator.navigate(LocationListRoute(storyId = route.storyId))
                true
            }
            "a" -> {
                navigator.navigate(ArcListRoute(storyId = route.storyId))
                true
            }
            "w" -> {
                navigator.navigate(WorldRuleListRoute(storyId = route.storyId))
                true
            }
            "v" -> {
                navigator.navigate(VolumeListRoute(storyId = route.storyId))
                true
            }
            "h" -> {
                navigator.navigate(ChapterListRoute(storyId = route.storyId))
                true
            }
            "s" -> {
                navigator.navigate(SceneListRoute(storyId = route.storyId))
                true
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

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text("Story Chat", style = theme.primary + TextStyle(bold = true))
            }
        }
        item { Spacer(Modifier.height(1)) }
        item { HorizontalDivider(DividerStyle.Light, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            item { EmptyState(title = "Loading story data...", titleStyle = theme.muted) }
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            item { EmptyState(title = error, titleStyle = theme.error) }
            return@LazyColumn
        }

        val story = state.story
        if (story == null) {
            item { EmptyState(title = "No story found for this session.", titleStyle = theme.muted) }
            return@LazyColumn
        }

        val titleValue = story.title ?: "(empty)"
        val titleStyle = when {
            titleValue == StoryRecord.PLACEHOLDER_TITLE -> theme.error
            titleValue == "(empty)" -> theme.warning
            else -> theme.primary
        }
        item { labelValueRow("Title", titleValue, highlightEmpty = false, valueStyle = titleStyle) }
        item { labelValueRow("Genre", story.genre ?: "(empty)", required = true) }
        item { labelValueRow("Setting", story.setting ?: "(empty)", required = true) }
        item { labelValueRow("Status", story.status?.name ?: "(empty)", required = true) }

        item { Spacer(Modifier.height(1)) }
        item { HorizontalDivider(DividerStyle.Light, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }
        item { SectionHeader(title = "Story Style") }
        val style = story.styleProfile
        item { labelValueRow("Logline", style?.logline ?: "(empty)", required = true) }
        item { labelValueRow("Tone", style?.tone ?: "(empty)", required = true) }
        item { labelValueRow("POV", style?.pov ?: "(empty)", required = true) }
        item { labelValueRow("Tense", style?.tense ?: "(empty)", required = true) }
        item { labelValueRow("Pacing", style?.pacing ?: "(empty)", required = true) }

        item { Spacer(Modifier.height(1)) }
        item { HorizontalDivider(DividerStyle.Light, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }
        item { SectionHeader(title = "Library") }
        item { Spacer(Modifier.height(1)) }

        if (state.counts.isEmpty()) {
            item { EmptyState(title = "(none)", titleStyle = theme.muted) }
        } else {
            item {
                CountTileGrid(
                    items = state.counts.map { CountTile(it.label, it.count) },
                    cells = GridCells.Adaptive(minSize = 18),
                    gap = 2,
                    leftPadding = 2,
                    styleForCount = { count -> countStyle(count, theme) },
                )
            }
        }

        item { Spacer(Modifier.height(1)) }
        item {
            KeyHintBar(
                hints = listOf(
                    KeyHint("C", EntityOptionType.CHARACTERS.title.lowercase()),
                    KeyHint("L", EntityOptionType.LOCATIONS.title.lowercase()),
                    KeyHint("A", EntityOptionType.ARCS.title.lowercase()),
                    KeyHint("W", EntityOptionType.WORLD_RULES.title.lowercase()),
                    KeyHint("V", EntityOptionType.VOLUMES.title.lowercase()),
                    KeyHint("H", EntityOptionType.CHAPTERS.title.lowercase()),
                    KeyHint("S", EntityOptionType.SCENES.title.lowercase()),
                    KeyHint("Esc", "back"),
                ),
                keyStyle = theme.muted,
                descriptionStyle = theme.muted,
                separatorStyle = theme.muted,
            )
        }
    }
}

@Dispatchable
private fun labelValueRow(
    label: String,
    value: String,
    required: Boolean = false,
    highlightEmpty: Boolean = true,
    valueStyle: TextStyle? = null,
) {
    val theme = LocalTheme.current
    val resolvedStyle = valueStyle ?: fieldStyle(value, required, highlightEmpty)
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text("$label:", style = theme.muted)
        Spacer(Modifier.width(2))
        Text(value, style = resolvedStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun fieldStyle(value: String, required: Boolean, highlightEmpty: Boolean): TextStyle {
    val theme = LocalTheme.current
    if (!highlightEmpty) return theme.primary
    if (value == "(empty)" || value.isBlank()) {
        return if (required) theme.warning else theme.muted
    }
    return theme.primary
}

private fun countStyle(count: Int, theme: DispatchTheme): TextStyle {
    return when {
        count <= 0 -> TextColors.brightRed
        count <= 2 -> theme.info
        else -> theme.success
    }
}
