@file:Suppress("ktlint:standard:function-naming")

package com.ead.dispatch.sample.presentation.components

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.Surface
import com.ead.dispatch.widget.SurfaceStyle
import com.ead.dispatch.widget.HorizontalDivider
import com.ead.dispatch.widget.Panel
import com.ead.dispatch.sample.widgets.Section
import com.ead.dispatch.sample.widgets.SectionHeader
import com.ead.dispatch.widget.ChipRow
import com.ead.dispatch.modifier.BorderStyle
import com.ead.dispatch.widget.Chip
import com.ead.dispatch.widget.Text
import com.ead.dispatch.widget.TextField
import com.ead.dispatch.widget.BasicTextFieldRenderer
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
        Text("Styled builder text")
        Text("**Markdown** with `code`", markdown = true)
        HorizontalDivider(modifier = Modifier.fillMaxWidth())
        Row {
            Text("left")
            Spacer(Modifier.width(1))
            VerticalDivider(Modifier.height(1))
            Spacer(Modifier.width(1))
            Text("right")
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            style = SurfaceStyle.fill(rgb("#303846"), paddingVertical = 0),
        ) {
            Text("Surface fill")
        }
        Panel(title = "Panel") { Text("Bordered content") }
        Panel(title = "Rounded", borderStyle = BorderStyle.Rounded) { Text("Convenience panel") }
        Section("Section") { Text("Header-line grouping") }
        Chip("single")
        ChipRow(listOf("stable", "reactive", "terminal"))
        BasicTextFieldRenderer(state = fieldState, showCursor = false)
        TextField(state = filterState, showCursor = false)
        Surface { TextField(state = filterState, showCursor = false) }
    }
}
