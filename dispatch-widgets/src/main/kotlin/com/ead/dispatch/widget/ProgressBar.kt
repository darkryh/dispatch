package com.ead.dispatch.widget

import com.ead.dispatch.annotation.Dispatchable
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
 * ProgressBar(progress = 0.75f)
 * ProgressBar(
 *     progress = downloadProgress,
 *     modifier = Modifier.fillMaxWidth(),
 *     style = ProgressBarStyle.Blocks,
 * )
 * ```
 *
 * @param progress Progress value from 0.0 to 1.0.
 * @param modifier Modifiers to apply.
 * @param style Visual style for the progress bar.
 * @param showPercentage Whether to show percentage text.
 */
@Dispatchable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    style: ProgressBarStyle = ProgressBarStyle.Blocks,
    showPercentage: Boolean = false,
) = composableWidget("ProgressBar") {
    ProgressBarMeasurable(
        progress = progress.coerceIn(0f, 1f),
        modifier = modifier,
        style = style,
        showPercentage = showPercentage,
    )
}

/**
 * Progress bar visual styles.
 */
enum class ProgressBarStyle(
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

internal class ProgressBarMeasurable(
    private val progress: Float,
    override val modifier: Modifier,
    private val style: ProgressBarStyle,
    private val showPercentage: Boolean,
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val percentageText = if (showPercentage) {
            " ${(progress * 100).toInt()}%"
        } else {
            ""
        }

        val width = if (modifiedConstraints.hasBoundedWidth) {
            modifiedConstraints.maxWidth
        } else {
            20 + percentageText.length
        }

        val barWidth = (width - 2 - percentageText.length).coerceAtLeast(0) // -2 for caps
        val filledWidth = (barWidth * progress).toInt()
        val emptyWidth = barWidth - filledWidth

        val bar = buildString {
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
@Dispatchable
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
enum class SpinnerStyle(val frames: List<String>) {
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
@Dispatchable
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

private fun positiveModulo(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    val result = value % modulus
    return if (result < 0) result + modulus else result
}

/**
 * A download/upload progress indicator.
 *
 * Example:
 * ```kotlin
 * TransferProgress(
 *     progress = 0.65f,
 *     bytesTransferred = 6_500_000,
 *     totalBytes = 10_000_000,
 * )
 * ```
 */
@Dispatchable
fun TransferProgress(
    progress: Float,
    bytesTransferred: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    style: ProgressBarStyle = ProgressBarStyle.Blocks,
) = composableWidget("TransferProgress") {
    TransferProgressMeasurable(
        progress = progress.coerceIn(0f, 1f),
        bytesTransferred = bytesTransferred,
        totalBytes = totalBytes,
        modifier = modifier,
        style = style,
    )
}

internal class TransferProgressMeasurable(
    private val progress: Float,
    private val bytesTransferred: Long,
    private val totalBytes: Long,
    override val modifier: Modifier,
    private val style: ProgressBarStyle,
) : Measurable {

    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val width = if (modifiedConstraints.hasBoundedWidth) {
            modifiedConstraints.maxWidth
        } else {
            50
        }

        // Format bytes
        val transferred = formatBytes(bytesTransferred)
        val total = formatBytes(totalBytes)
        val percentage = "${(progress * 100).toInt()}%"

        val info = " $transferred / $total ($percentage)"
        val barWidth = (width - info.length - 2).coerceAtLeast(10)

        val filledWidth = (barWidth * progress).toInt()
        val emptyWidth = barWidth - filledWidth

        val bar = buildString {
            append(style.leftCap)
            repeat(filledWidth) { append(style.filled) }
            repeat(emptyWidth) { append(style.empty) }
            append(style.rightCap)
            append(info)
        }

        return SimplePlaceable(
            width = bar.length,
            height = 1,
            lines = listOf(bar),
        )
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> String.format("%.1fGB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format("%.1fMB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format("%.1fKB", bytes / 1_000.0)
            else -> "${bytes}B"
        }
    }
}
