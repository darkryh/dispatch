package com.ead.dispatch.sample.presentation.story_chat

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
import com.ead.dispatch.runtime.LocalTerminalWidth
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.sample.domain.entity.EntityOptionType
import com.ead.dispatch.sample.navigation.ArcListRoute
import com.ead.dispatch.sample.navigation.CharacterListRoute
import com.ead.dispatch.sample.navigation.LocationListRoute
import com.ead.dispatch.sample.navigation.StoryChatRoute
import com.ead.dispatch.sample.navigation.WorldRuleListRoute
import com.ead.dispatch.sample.data.db.entities.StoryRecord
import com.ead.dispatch.state.getValue
import com.ead.dispatch.viewmodel.collectAsState
import com.ead.dispatch.viewmodel.viewModel
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.DividerStyle
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextOverflow
import com.github.ajalt.mordant.input.KeyboardEvent
import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyle

@Dispatchable
fun StoryChatScreen(backStack: NavBackStack<NavKey>, route: StoryChatRoute) {
    val theme = LocalTheme.current
    val keyboardInterceptor = LocalKeyboardInterceptor.current
    val viewModel = viewModel<StoryChatViewModel>()
    val state by viewModel.state.collectAsState()
    fun handleKeyEvent(event: KeyboardEvent): Boolean {
        val consumed = when (event.key.lowercase()) {
            "escape", "esc" -> {
                backStack.popBackStack()
                true
            }
            "c" -> {
                backStack.navigate(CharacterListRoute(storyId = route.storyId))
                true
            }
            "l" -> {
                backStack.navigate(LocationListRoute(storyId = route.storyId))
                true
            }
            "a" -> {
                backStack.navigate(ArcListRoute(storyId = route.storyId))
                true
            }
            "w" -> {
                backStack.navigate(WorldRuleListRoute(storyId = route.storyId))
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
        item { HorizontalDivider(DividerStyle.Heavy, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }

        if (state.isLoading) {
            item { rowText("Loading story data...", theme.muted) }
            return@LazyColumn
        }

        val error = state.error
        if (error != null) {
            item { rowText(error, theme.error) }
            return@LazyColumn
        }

        val story = state.story
        if (story == null) {
            item { rowText("No story found for this session.", theme.muted) }
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
        item { HorizontalDivider(DividerStyle.Heavy, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }
        item { sectionHeader("Story Style") }
        val style = story.styleProfile
        item { labelValueRow("Logline", style?.logline ?: "(empty)", required = true) }
        item { labelValueRow("Tone", style?.tone ?: "(empty)", required = true) }
        item { labelValueRow("POV", style?.pov ?: "(empty)", required = true) }
        item { labelValueRow("Tense", style?.tense ?: "(empty)", required = true) }
        item { labelValueRow("Pacing", style?.pacing ?: "(empty)", required = true) }

        item { Spacer(Modifier.height(1)) }
        item { HorizontalDivider(DividerStyle.Heavy, modifier = Modifier.fillMaxWidth()) }
        item { Spacer(Modifier.height(1)) }
        item { sectionHeader("Library") }
        item { Spacer(Modifier.height(1)) }

        val terminalWidth = LocalTerminalWidth.current
        val countGrid = buildCountGrid(state.counts, terminalWidth)
        if (countGrid.rows.isEmpty()) {
            item { rowText("(none)", theme.muted) }
        } else {
            countGrid.rows.forEach { row ->
                item { countRow(row, countGrid.tileWidth) }
            }
        }

        item { Spacer(Modifier.height(1)) }
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.width(2))
                Text(
                    text = "Shortcuts: C ${EntityOptionType.CHARACTERS.title.lowercase()} | " +
                        "L ${EntityOptionType.LOCATIONS.title.lowercase()} | " +
                        "A ${EntityOptionType.ARCS.title.lowercase()} | " +
                        "W ${EntityOptionType.WORLD_RULES.title.lowercase()} | Esc back",
                    style = theme.muted,
                )
            }
        }
    }
}

@Dispatchable
private fun sectionHeader(title: String) {
    val theme = LocalTheme.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text(title, style = theme.secondary + TextStyle(bold = true))
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

@Dispatchable
private fun rowText(text: String, style: TextStyle) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        Text(text, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Dispatchable
private fun countRow(items: List<StoryCountItem>, tileWidth: Int) {
    val theme = LocalTheme.current
    val gap = 2
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(2))
        items.forEachIndexed { index, item ->
            countTile(item, tileWidth, theme)
            if (index != items.lastIndex) {
                Spacer(Modifier.width(gap))
            }
        }
    }
}

private fun buildCountGrid(items: List<StoryCountItem>, width: Int): CountGrid {
    if (items.isEmpty()) return CountGrid(emptyList(), 0)
    val leftPadding = 2
    val gap = 2
    val minTileWidth = 18
    val maxLabel = items.maxOf { it.label.length }
    val maxCountDigits = items.maxOf { it.count.toString().length }
    val tileWidth = (maxLabel + maxCountDigits + 4).coerceAtLeast(minTileWidth)
    val available = (width - leftPadding).coerceAtLeast(tileWidth)
    val perRow = ((available + gap) / (tileWidth + gap)).coerceAtLeast(1)

    val rows = mutableListOf<List<StoryCountItem>>()
    var index = 0
    while (index < items.size) {
        val slice = items.drop(index).take(perRow)
        rows.add(slice)
        index += perRow
    }
    return CountGrid(rows, tileWidth)
}

private fun buildTile(item: StoryCountItem, width: Int): String {
    val countText = item.count.toString()
    val maxLabelLength = (width - countText.length - 4).coerceAtLeast(1)
    val label = if (item.label.length > maxLabelLength) {
        val cut = (maxLabelLength - 3).coerceAtLeast(1)
        item.label.take(cut) + "..."
    } else {
        item.label
    }
    val raw = "[${label}] $countText"
    return if (raw.length >= width) raw.take(width) else raw.padEnd(width)
}

@Dispatchable
private fun countTile(item: StoryCountItem, width: Int, theme: com.ead.dispatch.theme.DispatchTheme) {
    val countText = item.count.toString()
    val maxLabelLength = (width - countText.length - 4).coerceAtLeast(1)
    val label = if (item.label.length > maxLabelLength) {
        val cut = (maxLabelLength - 3).coerceAtLeast(1)
        item.label.take(cut) + "..."
    } else {
        item.label
    }

    Row {
        Text(buildTile(item, width), style = countStyle(item.count, theme))
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

private fun countStyle(count: Int, theme: com.ead.dispatch.theme.DispatchTheme): TextStyle {
    return when {
        count <= 0 -> TextColors.brightRed
        count <= 2 -> theme.info
        else -> theme.success
    }
}

private data class CountGrid(
    val rows: List<List<StoryCountItem>>,
    val tileWidth: Int,
)
