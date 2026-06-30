@file:Suppress("ktlint:standard:function-naming")

package io.github.darkryh.dispatch.sample.widgets

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.Placeable
import io.github.darkryh.dispatch.layout.SimplePlaceable
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.applyToConstraints
import io.github.darkryh.dispatch.runtime.composableWidget
import io.github.darkryh.dispatch.widget.LinearProgressIndicatorStyle

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
@Composable
fun TransferProgress(
    progress: Float,
    bytesTransferred: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier,
    style: LinearProgressIndicatorStyle = LinearProgressIndicatorStyle.Blocks,
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
    private val style: LinearProgressIndicatorStyle,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val width =
            if (modifiedConstraints.hasBoundedWidth) {
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

        val bar =
            buildString {
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

    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1_000_000_000 -> String.format(java.util.Locale.ROOT, "%.1fGB", bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1fMB", bytes / 1_000_000.0)
            bytes >= 1_000 -> String.format(java.util.Locale.ROOT, "%.1fKB", bytes / 1_000.0)
            else -> "${bytes}B"
        }
}
