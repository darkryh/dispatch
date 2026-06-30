package io.github.darkryh.dispatch.widget

import androidx.compose.runtime.Composable
import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.layout.Measurable
import io.github.darkryh.dispatch.layout.Placeable
import io.github.darkryh.dispatch.layout.SimplePlaceable
import io.github.darkryh.dispatch.modifier.Modifier
import io.github.darkryh.dispatch.modifier.applyToConstraints
import io.github.darkryh.dispatch.runtime.LocalTerminal
import io.github.darkryh.dispatch.runtime.composableContainer
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Text as MordantText

data class SurfaceRule(
    val char: Char = '─',
    val style: TextStyle? = null,
)

data class SurfaceBox(
    val paddingHorizontal: Int = 1,
    val paddingVertical: Int = 0,
)

/**
 * Styling for [Surface].
 *
 * Notes:
 * - [fill] is applied to the full available width (including trailing spaces).
 * - If [fill] specifies only a foreground color (e.g. `rgb("#ffffff")`), it is treated as a
 *   background fill.
 */
data class SurfaceStyle(
    val topRule: SurfaceRule? = null,
    val bottomRule: SurfaceRule? = null,
    val fill: TextStyle? = null,
    val box: SurfaceBox? = null,
) {
    companion object {
        val None: SurfaceStyle = SurfaceStyle()

        fun lines(
            char: Char = '─',
            style: TextStyle? = null,
        ): SurfaceStyle =
            SurfaceStyle(
                topRule = SurfaceRule(char = char, style = style),
                bottomRule = SurfaceRule(char = char, style = style),
            )

        fun fill(
            fill: TextStyle,
            paddingHorizontal: Int = 1,
            paddingVertical: Int = 1,
        ): SurfaceStyle =
            SurfaceStyle(
                fill = fill,
                box =
                    SurfaceBox(
                        paddingHorizontal = paddingHorizontal,
                        paddingVertical = paddingVertical,
                    ),
            )
    }
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    style: SurfaceStyle = SurfaceStyle.None,
    content: @Composable () -> Unit,
) {
    val terminal = LocalTerminal.current
    composableContainer(
        name = "Surface",
        modifier = modifier,
        measurableFactory = { children -> SurfaceMeasurable(modifier, style, children, terminal) },
        content = content,
    )
}

internal class SurfaceMeasurable(
    override val modifier: Modifier,
    private val style: SurfaceStyle,
    private val children: List<Measurable>,
    private val terminal: Terminal,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)

        val box = style.box
        val fillStyle = style.fill?.let { normalizeFillStyle(it) }

        val childMaxWidth =
            if (box != null && modifiedConstraints.hasBoundedWidth) {
                val innerWidth = modifiedConstraints.maxWidth.coerceAtLeast(0)
                val paddingHorizontal = box.paddingHorizontal.coerceIn(0, innerWidth / 2)
                (innerWidth - (paddingHorizontal * 2)).coerceAtLeast(0)
            } else {
                modifiedConstraints.maxWidth
            }

        val childMaxHeight =
            if (box != null && modifiedConstraints.hasBoundedHeight) {
                val innerHeight = modifiedConstraints.maxHeight.coerceAtLeast(0)
                val paddingVertical = box.paddingVertical.coerceIn(0, innerHeight / 2)
                (innerHeight - (paddingVertical * 2)).coerceAtLeast(0)
            } else {
                modifiedConstraints.maxHeight
            }

        val childConstraints =
            Constraints(
                minWidth = 0,
                maxWidth = childMaxWidth,
                minHeight = 0,
                maxHeight = childMaxHeight,
            )

        val childPlaceables =
            children.map { measurable ->
                measurable.measure(measurable.modifier.applyToConstraints(childConstraints))
            }

        val contentMaxWidth =
            childPlaceables
                .flatMap { it.lines }
                .maxOfOrNull { displayWidth(it) }
                ?: 0

        val width =
            if (modifiedConstraints.hasBoundedWidth) {
                modifiedConstraints.maxWidth
            } else {
                val target =
                    when {
                        box != null -> {
                            val paddingHorizontal = box.paddingHorizontal.coerceAtLeast(0)
                            contentMaxWidth + (paddingHorizontal * 2)
                        }
                        else -> contentMaxWidth
                    }
                modifiedConstraints.constrainWidth(target)
            }

        val lines =
            if (box != null) {
                renderBox(
                    width = width,
                    box = box,
                    fillStyle = fillStyle,
                    contentLines = childPlaceables.flatMap { it.lines },
                )
            } else {
                renderRules(
                    width = width,
                    topRule = style.topRule,
                    bottomRule = style.bottomRule,
                    fillStyle = fillStyle,
                    contentLines = childPlaceables.flatMap { it.lines },
                )
            }

        val maxHeight = if (modifiedConstraints.hasBoundedHeight) modifiedConstraints.maxHeight else Int.MAX_VALUE
        val finalLines = lines.take(maxHeight)

        return SimplePlaceable(
            width = width,
            height = finalLines.size,
            lines = finalLines,
        )
    }

    private fun renderRules(
        width: Int,
        topRule: SurfaceRule?,
        bottomRule: SurfaceRule?,
        fillStyle: TextStyle?,
        contentLines: List<String>,
    ): List<String> {
        val lines = mutableListOf<String>()

        topRule?.let { rule ->
            lines += renderRuleLine(rule, width)
        }

        if (fillStyle == null) {
            lines += contentLines
        } else {
            contentLines.forEach { line ->
                val paddedLine = padToDisplayWidth(line, width)
                lines += applyFillPreservingInnerStyles(fillStyle, paddedLine)
            }
        }

        bottomRule?.let { rule ->
            lines += renderRuleLine(rule, width)
        }

        return lines
    }

    private fun renderBox(
        width: Int,
        box: SurfaceBox,
        fillStyle: TextStyle?,
        contentLines: List<String>,
    ): List<String> {
        val innerWidth = width.coerceAtLeast(0)

        val paddingHorizontal = box.paddingHorizontal.coerceAtLeast(0).coerceIn(0, innerWidth / 2)
        val contentWidth = (innerWidth - (paddingHorizontal * 2)).coerceAtLeast(0)
        val paddingVertical = box.paddingVertical.coerceAtLeast(0)

        val lines = mutableListOf<String>()

        val innerLines =
            buildList {
                repeat(paddingVertical) { add("") }
                if (contentLines.isEmpty()) add("") else addAll(contentLines)
                repeat(paddingVertical) { add("") }
            }

        for ((_, line) in innerLines.withIndex()) {
            val padded = padToDisplayWidth(line, contentWidth)
            val innerRaw = " ".repeat(paddingHorizontal) + padded + " ".repeat(paddingHorizontal)

            val innerStyled = fillStyle?.let { applyFillPreservingInnerStyles(it, innerRaw) } ?: innerRaw

            lines += innerStyled
        }

        return lines
    }

    private fun renderRuleLine(
        rule: SurfaceRule,
        width: Int,
    ): String {
        if (width <= 0) return ""
        val text = rule.char.toString().repeat(width)
        return rule.style?.invoke(text) ?: text
    }

    private fun applyFillPreservingInnerStyles(
        fillStyle: TextStyle,
        text: String,
    ): String {
        val (prefix, suffix) = splitStyleWrapper(fillStyle)
        if (prefix.isEmpty() && suffix.isEmpty()) return text

        if (!text.contains('\u001B')) {
            return prefix + text + suffix
        }

        // Child widgets (e.g. `Text`) can emit SGR reset codes mid-line (especially when combining
        // multiple styled segments). If a segment resets the background, it would clear our fill
        // background for the remainder of the line (including padding/trailing spaces). Detect
        // background resets and re-apply the fill afterward.
        return prefix + reapplyFillAfterBackgroundResets(prefix, text) + suffix
    }

    private fun reapplyFillAfterBackgroundResets(
        fillPrefix: String,
        text: String,
    ): String {
        val sb = StringBuilder(text.length + 16)
        var index = 0
        while (index < text.length) {
            val c = text[index]
            if (c != '\u001B' || index + 1 >= text.length || text[index + 1] != '[') {
                sb.append(c)
                index++
                continue
            }

            val start = index
            var end = index + 2
            while (end < text.length && text[end] != 'm') end++
            if (end >= text.length) {
                // Unterminated sequence, append rest and stop.
                sb.append(text.substring(start))
                break
            }

            val sequence = text.substring(start, end + 1)
            sb.append(sequence)

            val codesRaw = sequence.substring(2, sequence.length - 1)
            val codes = if (codesRaw.isEmpty()) listOf("0") else codesRaw.split(';')
            if (codes.any { it == "0" || it == "49" }) {
                sb.append(fillPrefix)
            }

            index = end + 1
        }

        return sb.toString()
    }

    private fun splitStyleWrapper(style: TextStyle): Pair<String, String> {
        val sentinel = "§§DISPATCH_BG_SENTINEL§§"
        val styled = style.invoke(sentinel)
        val index = styled.indexOf(sentinel)
        if (index < 0) return "" to ""
        return styled.substring(0, index) to styled.substring(index + sentinel.length)
    }

    private fun normalizeFillStyle(fillStyle: TextStyle): TextStyle {
        // Most call sites will pass a foreground color (e.g. `rgb("#ffffff")`) expecting it to be
        // used as a background fill. Convert to a background style unless a bg color is already set.
        return when {
            fillStyle.bgColor != null -> fillStyle
            fillStyle.color != null -> fillStyle.bg
            else -> fillStyle
        }
    }

    private fun displayWidth(text: String): Int =
        MordantText(
            text,
            whitespace = Whitespace.PRE,
        ).render(terminal, width = 10_000).width

    private fun padToDisplayWidth(
        text: String,
        targetWidth: Int,
    ): String {
        if (targetWidth <= 0) return ""

        val renderedWidth = displayWidth(text)

        if (renderedWidth >= targetWidth) return text
        return text + " ".repeat(targetWidth - renderedWidth)
    }

    private companion object
}
