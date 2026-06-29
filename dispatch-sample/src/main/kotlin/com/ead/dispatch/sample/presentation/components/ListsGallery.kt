@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.runtime.LocalTheme
import com.ead.dispatch.widget.LazyColumn
import com.ead.dispatch.widget.ScrollableList
import com.ead.dispatch.widget.ScrollableListWithIndicator
import com.ead.dispatch.sample.widgets.SectionHeader
import com.ead.dispatch.sample.widgets.SessionDisplayColumn
import com.ead.dispatch.sample.widgets.SessionOption
import com.ead.dispatch.sample.widgets.SessionSelector
import com.ead.dispatch.widget.SelectableList
import com.ead.dispatch.widget.SelectableListStyles
import com.ead.dispatch.widget.SelectableWindowedList
import com.ead.dispatch.widget.Text

@Composable
internal fun ListsGallery() {
    val theme = LocalTheme.current
    val styles =
        SelectableListStyles(
            prefix = theme.muted,
            selectedPrefix = theme.accent,
        )
    val values = listOf("Alpha", "Beta", "Gamma", "Delta")

    GalleryScreen("Lists", "Scrolling, windowing, selection, and session tables") {
        SectionHeader("ScrollableList")
        ScrollableList(items = values, modifier = Modifier.fillMaxWidth().height(2)) { Text(it) }
        SectionHeader("ScrollableListWithIndicator")
        ScrollableListWithIndicator(
            items = values,
            modifier = Modifier.fillMaxWidth().height(2),
        ) { Text(it) }
        SectionHeader("SelectableList")
        SelectableList(items = values.take(2), selectedIndex = 1, styles = styles) { item, _ -> Text(item) }
        SectionHeader("SelectableWindowedList")
        SelectableWindowedList(
            items = values,
            selectedIndex = 2,
            visibleCount = 2,
            styles = styles,
        ) { item, _ -> Text(item) }
        SectionHeader("LazyColumn")
        LazyColumn(modifier = Modifier.fillMaxWidth().height(2)) {
            items(values, key = { it }) { Text("Lazy $it") }
        }
        SectionHeader("SessionSelector")
        SessionSelector(
            options =
                listOf(
                    SessionOption("1", "Focus validation", "now", "sample-1", 3, "one"),
                    SessionOption("2", "Streaming validation", "1m", "sample-2", 6, "two"),
                ),
            onOptionSelected = {},
            onExit = {},
            columns = listOf(SessionDisplayColumn.TITLE, SessionDisplayColumn.MESSAGE_COUNT),
            visibleCount = 2,
            showFilter = false,
            enabled = false,
        )
    }
}
