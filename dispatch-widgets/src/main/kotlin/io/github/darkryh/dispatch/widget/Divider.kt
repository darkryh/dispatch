package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.Placeable
import io.github.darkryh.dispatch.layout.SimplePlaceable
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.applyToConstraints
import io.github.darkryh.dispatch.runtime.composableWidget

/**
 * A horizontal divider line.
 *
 * Example:
 * ```kotlin
 * Column {
 *     Text("Above")
 *     HorizontalDivider()
 *     Text("Below")
 * }
 * ```
 *
 * @param modifier Modifiers to apply.
 * @param char Character to use for the divider (default: ─).
 */
@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    char: Char = '─',
) = composableWidget("HorizontalDivider") {
    HorizontalDividerMeasurable(modifier, char)
}

internal class HorizontalDividerMeasurable(
    override val modifier: Modifier,
    private val char: Char,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)
        val width =
            if (modifiedConstraints.hasBoundedWidth) {
                modifiedConstraints.maxWidth
            } else {
                1
            }

        return SimplePlaceable(
            width = width,
            height = 1,
            lines = listOf(char.toString().repeat(width)),
        )
    }
}

/**
 * A vertical divider line.
 *
 * Example:
 * ```kotlin
 * Row {
 *     Text("Left")
 *     VerticalDivider()
 *     Text("Right")
 * }
 * ```
 *
 * @param modifier Modifiers to apply.
 * @param char Character to use for the divider (default: │).
 */
@Composable
fun VerticalDivider(
    modifier: Modifier = Modifier,
    char: Char = '│',
) = composableWidget("VerticalDivider") {
    VerticalDividerMeasurable(modifier, char)
}

internal class VerticalDividerMeasurable(
    override val modifier: Modifier,
    private val char: Char,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)
        val height =
            if (modifiedConstraints.hasBoundedHeight) {
                modifiedConstraints.maxHeight
            } else {
                1
            }

        return SimplePlaceable(
            width = 1,
            height = height,
            lines = List(height) { char.toString() },
        )
    }
}

/**
 * Style options for dividers.
 */
enum class DividerStyle(
    val horizontal: Char,
    val vertical: Char,
) {
    Light('─', '│'),
    Heavy('━', '┃'),
    Double('═', '║'),
    Dashed('┄', '┆'),
    Dotted('·', '·'),
    Space(' ', ' '),
}

/**
 * Styled horizontal divider.
 */
@Composable
fun HorizontalDivider(
    style: DividerStyle,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(modifier = modifier, char = style.horizontal)
}

/**
 * Styled vertical divider.
 */
@Composable
fun VerticalDivider(
    style: DividerStyle,
    modifier: Modifier = Modifier,
) {
    VerticalDivider(modifier = modifier, char = style.vertical)
}
