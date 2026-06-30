@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.surfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.width
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.ControlPanel
import io.github.darkryh.dispatch.sample.designsystem.ControlSpec
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundController
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundScaffold
import io.github.darkryh.dispatch.sample.widgets.LabeledValue
import io.github.darkryh.dispatch.sample.widgets.LabeledValueList
import io.github.darkryh.dispatch.sample.widgets.SectionHeader
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.Chip
import io.github.darkryh.dispatch.widget.ChipRow
import io.github.darkryh.dispatch.widget.DividerStyle
import io.github.darkryh.dispatch.widget.HorizontalDivider
import io.github.darkryh.dispatch.widget.Panel
import io.github.darkryh.dispatch.widget.Surface
import io.github.darkryh.dispatch.widget.SurfaceStyle
import io.github.darkryh.dispatch.widget.Text
import io.github.darkryh.dispatch.widget.TextOverflow
import io.github.darkryh.dispatch.widget.VerticalDivider
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb

/**
 * Surfaces & Dividers playground.
 *
 * Demonstrates: [Panel] (all [io.github.darkryh.dispatch.modifier.BorderStyle]s), [Surface] (`None` / `lines`
 * / `fill` [SurfaceStyle]s), [HorizontalDivider] (all [DividerStyle]s), [Text] overflow handling
 * (all [TextOverflow] modes) and Markdown rendering, [Chip] / [ChipRow], plus the sample composites
 * [SectionHeader] and [LabeledValueList] — each driven live from the controls pane.
 */
@Composable
fun SurfacesScreen(viewModel: SurfacesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Surfaces & Dividers",
        subtitle = "Cycle border/divider/surface/overflow styles with ←/→, flip Markdown with Space.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle(
                            "Border style",
                            io.github.darkryh.dispatch.modifier.BorderStyle.entries
                                .map { it.name },
                            state.borderStyle,
                        ),
                        ControlSpec.Cycle(
                            "Divider style",
                            DividerStyle.entries.map { it.name },
                            state.dividerStyle,
                        ),
                        ControlSpec.Cycle("Surface style", SURFACE_OPTIONS, state.surfaceStyle),
                        ControlSpec.Cycle(
                            "Text overflow",
                            TextOverflow.entries.map { it.name },
                            state.overflow,
                        ),
                        ControlSpec.Toggle("Markdown", state.markdown),
                    ),
                selected = state.selected,
            )
        },
        preview = { SurfacesPreview(state) },
    )
}

@Composable
private fun SurfacesPreview(state: SurfacesState) {
    val theme = LocalTheme.current
    Column {
        Text("Panel — ${state.borderStyleValue.name}", style = theme.muted)
        Panel(title = "Panel", borderStyle = state.borderStyleValue) {
            Text("Bordered content", style = theme.primary)
        }
        Spacer(Modifier.height(1))

        Text("Surface — ${SURFACE_OPTIONS[state.surfaceStyle]}", style = theme.muted)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            style =
                when (state.surfaceStyle) {
                    1 -> SurfaceStyle.lines('─', theme.muted)
                    2 -> SurfaceStyle.fill(rgb("#2A3340"))
                    else -> SurfaceStyle.None
                },
        ) {
            Text("Surface content", style = theme.info)
        }
        Spacer(Modifier.height(1))

        Text("HorizontalDivider — ${state.dividerStyleValue.name}", style = theme.muted)
        HorizontalDivider(style = state.dividerStyleValue, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(1))

        Text("VerticalDivider — ${state.dividerStyleValue.name}", style = theme.muted)
        Row {
            Text("left", style = theme.primary)
            VerticalDivider(style = state.dividerStyleValue, modifier = Modifier.height(1).width(3))
            Text("right", style = theme.primary)
        }
        Spacer(Modifier.height(1))

        Text("Text overflow — ${state.overflowValue.name}", style = theme.muted)
        Text(
            "This is a deliberately long single line of text that demonstrates how the chosen overflow mode behaves when it runs past the available width.",
            maxLines = 1,
            overflow = state.overflowValue,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("A normal, unconstrained line of text.", style = theme.primary)
        if (state.markdown) {
            Text("**bold** _italic_ `code`", markdown = true)
        }
        Spacer(Modifier.height(1))

        Text("Chips", style = theme.muted)
        Row {
            Chip("chip")
            Spacer(Modifier.width(2))
            ChipRow(listOf("alpha", "beta", "gamma"))
        }
        Spacer(Modifier.height(1))

        SectionHeader(title = "Composites", subtitle = "sample widgets")
        LabeledValueList(
            items =
                listOf(
                    LabeledValue("Border", state.borderStyleValue.name),
                    LabeledValue("Divider", state.dividerStyleValue.name),
                    LabeledValue("Surface", SURFACE_OPTIONS[state.surfaceStyle]),
                    LabeledValue("Overflow", state.overflowValue.name),
                    LabeledValue("Markdown", if (state.markdown) "on" else "off"),
                ),
            labelStyle = theme.muted,
            valueStyle = theme.info,
        )
    }
}
