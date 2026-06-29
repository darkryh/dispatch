package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.composableWidget
import com.github.ajalt.mordant.rendering.TextStyle

/**
 * A horizontal progress bar.
 *
 * Example:
 * ```kotlin
 * LinearProgressIndicator(progress = 0.75f)
 * LinearProgressIndicator(
 *     progress = downloadProgress,
 *     modifier = Modifier.fillMaxWidth(),
 *     style = LinearProgressIndicatorStyle.Blocks,
 * )
 * ```
 *
 * @param progress Progress value from 0.0 to 1.0.
 * @param modifier Modifiers to apply.
 * @param style Visual style for the progress bar.
 * @param showPercentage Whether to show percentage text.
 */
@Composable
fun LinearProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    style: LinearProgressIndicatorStyle = LinearProgressIndicatorStyle.Blocks,
    showPercentage: Boolean = false,
) = composableWidget("LinearProgressIndicator") {
    LinearProgressIndicatorMeasurable(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier,
        style = style,
        showPercentage = showPercentage,
    )
}

/**
 * Progress bar visual styles.
 */
enum class LinearProgressIndicatorStyle(
    val filled: Char,
    val empty: Char,
    val leftCap: Char,
    val rightCap: Char,
) {
    Blocks('█', '░', '[', ']'),
    Ascii('#', '-', '[', ']'),
    Dots('●', '○', '(', ')'),
    Arrows('▶', '▷', '|', '|'),
    Line('━', '─', '╺', '╸'),
    Minimal('■', '□', ' ', ' '),
}

internal class LinearProgressIndicatorMeasurable(
    private val progress: Float,
    override val modifier: Modifier,
    private val style: LinearProgressIndicatorStyle,
    private val showPercentage: Boolean,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val percentageText =
            if (showPercentage) {
                " ${(progress * 100).toInt()}%"
            } else {
                ""
            }

        val width =
            if (modifiedConstraints.hasBoundedWidth) {
                modifiedConstraints.maxWidth
            } else {
                20 + percentageText.length
            }

        val barWidth = (width - 2 - percentageText.length).coerceAtLeast(0) // -2 for caps
        val filledWidth = (barWidth * progress).toInt()
        val emptyWidth = barWidth - filledWidth

        val bar =
            buildString {
                append(style.leftCap)
                repeat(filledWidth) { append(style.filled) }
                repeat(emptyWidth) { append(style.empty) }
                append(style.rightCap)
                append(percentageText)
            }

        return SimplePlaceable(
            width = width,
            height = 1,
            lines = listOf(bar),
        )
    }
}

/**
 * An indeterminate progress spinner.
 *
 * Example:
 * ```kotlin
 * Spinner(frame = animationFrame)
 * ```
 *
 * @param frame Current animation frame (0-7).
 * @param modifier Modifiers to apply.
 * @param style Spinner style.
 */
@Composable
fun Spinner(
    frame: Int,
    modifier: Modifier = Modifier,
    style: SpinnerStyle = SpinnerStyle.Dots,
    textStyle: TextStyle? = null,
) = composableWidget("Spinner") {
    SpinnerMeasurable(
        frame = frame,
        modifier = modifier,
        style = style,
        textStyle = textStyle,
    )
}

/**
 * Spinner animation styles.
 */
enum class SpinnerStyle(
    val frames: List<String>,
) {
    Dots(listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")),
    Circle(listOf("◐", "◓", "◑", "◒")),
    Growing(listOf("▁", "▃", "▄", "▅", "▆", "▇", "█", "▇", "▆", "▅", "▄", "▃")),
    Pulse(listOf("·", "•", "●", "•")),
    Quadrants(listOf("▖", "▘", "▝", "▗")),
    Shades(listOf("░", "▒", "▓", "█", "▓", "▒", "░")),
}

internal class SpinnerMeasurable(
    private val frame: Int,
    override val modifier: Modifier,
    private val style: SpinnerStyle,
    private val textStyle: TextStyle? = null,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val frameIndex = positiveModulo(frame, style.frames.size)
        val char = style.frames[frameIndex]
        val renderedChar = textStyle?.invoke(char) ?: char

        return SimplePlaceable(
            width = char.length,
            height = 1,
            lines = listOf(renderedChar),
        )
    }
}

/**
 * A loading indicator with text.
 *
 * Example:
 * ```kotlin
 * LoadingIndicator(
 *     frame = animationFrame,
 *     text = "Loading...",
 * )
 * ```
 */
@Composable
fun LoadingIndicator(
    frame: Int,
    modifier: Modifier = Modifier,
    text: String = "Loading...",
    style: SpinnerStyle = SpinnerStyle.Dots,
) = composableWidget("LoadingIndicator") {
    LoadingIndicatorMeasurable(
        frame = frame,
        text = text,
        modifier = modifier,
        style = style,
    )
}

internal class LoadingIndicatorMeasurable(
    private val frame: Int,
    private val text: String,
    override val modifier: Modifier,
    private val style: SpinnerStyle,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val frameIndex = positiveModulo(frame, style.frames.size)
        val spinner = style.frames[frameIndex]
        val display = "$spinner $text"

        return SimplePlaceable(
            width = display.length,
            height = 1,
            lines = listOf(display),
        )
    }
}

private fun positiveModulo(
    value: Int,
    modulus: Int,
): Int {
    if (modulus <= 0) return 0
    val result = value % modulus
    return if (result < 0) result + modulus else result
}
