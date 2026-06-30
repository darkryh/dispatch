@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.presentation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Arrangement
import io.github.darkryh.dispatch.layout.Box
import io.github.darkryh.dispatch.layout.Column
import io.github.darkryh.dispatch.layout.FlowRow
import io.github.darkryh.dispatch.layout.HeaderLayout
import io.github.darkryh.dispatch.layout.Layout
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.MeasurePolicy
import io.github.darkryh.dispatch.layout.MeasureResult
import io.github.darkryh.dispatch.layout.Row
import io.github.darkryh.dispatch.layout.Spacer
import io.github.darkryh.dispatch.modifier.BorderStyle
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.applyToConstraints
import io.github.darkryh.dispatch.modifier.border
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.height
import io.github.darkryh.dispatch.modifier.padding
import io.github.darkryh.dispatch.modifier.verticalScroll
import io.github.darkryh.dispatch.runtime.LocalTheme
import io.github.darkryh.dispatch.sample.designsystem.ControlPanel
import io.github.darkryh.dispatch.sample.designsystem.ControlSpec
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundController
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundIntent
import io.github.darkryh.dispatch.sample.designsystem.PlaygroundScaffold
import io.github.darkryh.dispatch.viewmodel.viewModel
import io.github.darkryh.dispatch.widget.Text

/**
 * Layout playground.
 *
 * Demonstrates the `dispatch-layout` building blocks live from the controls pane:
 * [Box] (`contentAlignment`, every [Alignment.Alignment2D]), [Row] (`horizontalArrangement`, every
 * [Arrangement.Horizontal]), [FlowRow] (`maxItemsInEachRow` wrapping), a custom [Layout] with a
 * hand-written [MeasurePolicy] that staggers children diagonally, [HeaderLayout], and the
 * [Modifier.padding] / [Modifier.border] / [Modifier.verticalScroll] modifiers.
 */
@Composable
fun LayoutScreen(viewModel: LayoutViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()

    PlaygroundController(
        onSelect = { viewModel.sendIntent(PlaygroundIntent.Select(it)) },
        onChange = { viewModel.sendIntent(PlaygroundIntent.Change(it)) },
        onToggle = { viewModel.sendIntent(PlaygroundIntent.Toggle) },
    )

    PlaygroundScaffold(
        title = "Layout",
        subtitle = "Cycle alignment, arrangement, padding and flow wrapping with ←/→.",
        controls = {
            ControlPanel(
                specs =
                    listOf(
                        ControlSpec.Cycle("Box alignment", ALIGNMENTS.map { it.first }, state.alignment),
                        ControlSpec.Cycle("Row arrangement", ARRANGEMENTS.map { it.first }, state.arrangement),
                        ControlSpec.Cycle("Padding", PADDING_OPTIONS, state.padding),
                        ControlSpec.Cycle(
                            "Flow max items",
                            FLOW_OPTIONS.map { it.toString() },
                            FLOW_OPTIONS.indexOf(state.flowMax).coerceAtLeast(0),
                        ),
                    ),
                selected = state.selected,
            )
        },
        preview = { LayoutPreview(state) },
    )
}

/** The eight short tokens wrapped by the [FlowRow] demo. */
private val FLOW_TOKENS = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta")

@Composable
private fun LayoutPreview(state: LayoutState) {
    val theme = LocalTheme.current
    Column {
        Text("Box — ${ALIGNMENTS[state.alignment].first}", style = theme.muted)
        Box(modifier = Modifier.border(BorderStyle.Rounded, theme.border)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(5),
                contentAlignment = ALIGNMENTS[state.alignment].second,
            ) {
                Text("●", style = theme.accent)
            }
        }
        Spacer(Modifier.height(1))

        Text("Row — ${ARRANGEMENTS[state.arrangement].first}", style = theme.muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = ARRANGEMENTS[state.arrangement].second,
        ) {
            repeat(3) { index ->
                Text("[$index]", style = theme.info)
            }
        }
        Spacer(Modifier.height(1))

        Text("FlowRow — maxItemsInEachRow=${state.flowMax}", style = theme.muted)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1),
            verticalArrangement = Arrangement.spacedBy(0),
            maxItemsInEachRow = state.flowMax,
        ) {
            FLOW_TOKENS.forEach { token ->
                Text(token, style = theme.info)
            }
        }
        Spacer(Modifier.height(1))

        Text("Custom Layout — diagonal stagger", style = theme.muted)
        Layout(measurePolicy = StaggerMeasurePolicy(step = 2)) {
            repeat(4) { index ->
                Text("#$index", style = theme.accent)
            }
        }
        Spacer(Modifier.height(1))

        Text("Modifier.padding(${state.padding}) + border", style = theme.muted)
        Box(modifier = Modifier.border(BorderStyle.Ascii, theme.muted).padding(state.padding)) {
            Text("padded", style = theme.primary)
        }
        Spacer(Modifier.height(1))

        Text("Modifier.verticalScroll (3 visible lines)", style = theme.muted)
        Column(modifier = Modifier.height(3).verticalScroll()) {
            repeat(8) { index ->
                Text("scroll line ${index + 1}", style = theme.info)
            }
        }
        Spacer(Modifier.height(1))

        Text("HeaderLayout", style = theme.muted)
        HeaderLayout(
            modifier = Modifier.fillMaxWidth().height(3),
            header = { Text("— header —", style = theme.accent) },
        ) {
            Text("body fills the rest", style = theme.info)
        }
    }
}

/**
 * A minimal hand-written [MeasurePolicy] that places each child one row down and [step] columns to
 * the right of the previous one, producing a diagonal cascade. Demonstrates the low-level [Layout]
 * measure/place contract: measure each child against the incoming constraints, compute the bounding
 * size, then position the placeables in the placement block.
 */
private class StaggerMeasurePolicy(
    private val step: Int,
) : MeasurePolicy {
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
        val placeables =
            measurables.map { measurable ->
                measurable.measure(measurable.modifier.applyToConstraints(childConstraints))
            }

        var width = 0
        var height = 0
        placeables.forEachIndexed { index, placeable ->
            val right = index * step + placeable.width
            val bottom = index + placeable.height
            if (right > width) width = right
            if (bottom > height) height = bottom
        }

        val layoutWidth = if (constraints.hasBoundedWidth) constraints.constrainWidth(width) else width
        val layoutHeight = if (constraints.hasBoundedHeight) constraints.constrainHeight(height) else height

        return MeasureResult(width = layoutWidth, height = layoutHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.placeAt(x = index * step, y = index)
            }
        }
    }
}
