package io.github.darkryh.dispatch.layout

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.fillMaxSize
import io.github.darkryh.dispatch.modifier.fillMaxWidth
import io.github.darkryh.dispatch.modifier.weight

/**
 * Root contract for one terminal viewport.
 *
 * Header and footer consume their intrinsic height. The body receives all remaining rows and may
 * choose fixed, scrollable, or lazy content independently.
 */
@Composable
fun TerminalScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Layout(
        modifier = modifier,
        measurePolicy = TerminalScreenMeasurePolicy,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            header?.invoke()
        }
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            content = content,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            footer?.invoke()
        }
    }
}

private object TerminalScreenMeasurePolicy : MeasurePolicy {
    override fun measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val header = measurables.getOrNull(0)
        val body = measurables.getOrNull(1)
        val footer = measurables.getOrNull(2)
        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            )
        val footerPlaceable = footer?.measure(childConstraints)
        val headerPlaceable = header?.measure(childConstraints)
        val mainMaxHeight =
            if (constraints.hasBoundedHeight) {
                (constraints.maxHeight - (footerPlaceable?.height ?: 0) - (headerPlaceable?.height ?: 0)).coerceAtLeast(0)
            } else {
                constraints.maxHeight
            }
        val bodyPlaceable =
            body?.measure(
                Constraints(
                    minWidth = 0,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = mainMaxHeight,
                ),
            )
        val headerHeight = headerPlaceable?.height ?: 0
        val bodyHeight = bodyPlaceable?.height ?: 0
        val mainHeight = headerHeight + bodyHeight
        val footerHeight = footerPlaceable?.height ?: 0
        val totalHeight = constraints.constrainHeight(mainHeight + footerHeight)
        val width =
            constraints.constrainWidth(
                maxOf(headerPlaceable?.width ?: 0, bodyPlaceable?.width ?: 0, footerPlaceable?.width ?: 0),
            )

        return MeasureResult(
            width = width,
            height = totalHeight,
            activeStartLine = mainHeight.coerceAtMost(totalHeight),
            scrollingStartLine = headerHeight.coerceAtMost(totalHeight),
        ) {
            headerPlaceable?.placeAt(0, 0)
            bodyPlaceable?.placeAt(0, headerHeight)
            footerPlaceable?.placeAt(0, mainHeight)
        }
    }
}
