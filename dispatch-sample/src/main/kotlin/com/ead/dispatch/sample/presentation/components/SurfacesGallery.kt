@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Background
import com.ead.dispatch.widget.BackgroundStyle
import com.ead.dispatch.widget.Card
import com.ead.dispatch.widget.FilterBar
import com.ead.dispatch.widget.FilterBarCard
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.widget.Section
import com.ead.dispatch.widget.SectionHeader
import com.ead.dispatch.widget.StyledText
import com.ead.dispatch.widget.TagList
import com.ead.dispatch.widget.TagPill
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextField
import com.ead.dispatch.widget.VerticalDivider
import com.ead.dispatch.widget.rememberTextFieldState
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

@Composable
internal fun SurfacesGallery() {
    val fieldState = rememberTextFieldState("state-backed field")
    val filterState = rememberTextFieldState("dispatch")
    GalleryScreen("Surfaces", "Text, framing, separators, tags, and filter surfaces") {
        SectionHeader("Typography", subtitle = "plain, styled, and markdown")
        Text("Plain terminal text")
        StyledText {
            bold("Styled")
            append(" builder text")
        }
        Text("**Markdown** with `code`", markdown = true)
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Row {
            Text("left")
            Spacer(Modifier.width(1))
            VerticalDivider(Modifier.height(1))
            Spacer(Modifier.width(1))
            Text("right")
        }
        Background(
            modifier = Modifier.fillMaxWidth(),
            style = BackgroundStyle.fill(rgb("#303846"), paddingVertical = 0),
        ) {
            Text("Background fill")
        }
        Panel(title = "Panel") { Text("Bordered content") }
        Card(title = "Card") { Text("Convenience panel") }
        Section("Section") { Text("Header-line grouping") }
        TagPill("single")
        TagList(listOf("stable", "reactive", "terminal"))
        TextField(state = fieldState, showCursor = false)
        FilterBar(state = filterState, showCursor = false)
        FilterBarCard(state = filterState, showCursor = false)
    }
}
