package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.Placeable
import com.ead.dispatch.layout.SimplePlaceable
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.applyToConstraints
import com.ead.dispatch.runtime.HibernationRegistry
import com.ead.dispatch.runtime.LocalTerminal
import com.ead.dispatch.runtime.composableWidget
import com.github.ajalt.mordant.markdown.Markdown
import com.github.ajalt.mordant.rendering.Lines
import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.Span
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.rendering.TextStyle
import com.github.ajalt.mordant.rendering.Whitespace
import com.github.ajalt.mordant.rendering.Line as MordantLine
import com.github.ajalt.mordant.widgets.Text as MordantText

/**
 * Display styled text.
 *
 * Example:
 * ```kotlin
 * Text("Hello, World!")
 * Text("Bold text", style = TextStyle(bold = true))
 * Text("Long text that wraps", maxLines = 3, overflow = TextOverflow.Ellipsis)
 * ```
 *
 * @param text The text to display.
 * @param modifier Modifiers to apply.
 * @param style Text styling (bold, color, etc.).
 * @param align Text alignment within the available width.
 * @param maxLines Maximum number of lines (null for unlimited).
 * @param overflow How to handle text that exceeds maxLines.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    align: TextAlign = TextAlign.LEFT,
    maxLines: Int? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    markdown: Boolean = false,
) {
    val terminal = LocalTerminal.current
    composableWidget("Text") {
        TextMeasurable(
            text = text,
            modifier = modifier,
            style = style,
            align = align,
            maxLines = maxLines,
            overflow = overflow,
            markdown = markdown,
            terminal = terminal,
        )
    }
}

/**
 * How to handle text overflow.
 */
enum class TextOverflow {
    /**
     * Simply clip the text at the boundary.
     */
    Clip,

    /**
     * Add ellipsis (...) at the end of the last visible line.
     */
    Ellipsis,

    /**
     * Allow the text to overflow (visible beyond bounds).
     */
    Visible,
}

/**
 * Measurable implementation for Text.
 */
internal class TextMeasurable(
    private val text: String,
    override val modifier: Modifier,
    private val style: TextStyle?,
    private val align: TextAlign,
    private val maxLines: Int?,
    private val overflow: TextOverflow,
    private val markdown: Boolean,
    private val terminal: com.github.ajalt.mordant.terminal.Terminal,
) : Measurable {
    override fun measure(constraints: Constraints): Placeable {
        val modifiedConstraints = modifier.applyToConstraints(constraints)
        val maxWidth = modifiedConstraints.maxWidth.takeIf { it != Int.MAX_VALUE }

        // Mordant's `TextAlign.LEFT` pads the rendered line to the available width. In Dispatch, a Text
        // without an explicit width modifier should measure to its intrinsic width, so treat LEFT as
        // "no alignment / no padding".
        val effectiveAlign = if (align == TextAlign.LEFT) TextAlign.NONE else align

        // Use Mordant's renderer to handle wide/combining chars, wrapping, and alignment.
        val renderedWidth = maxWidth ?: 10_000
        val renderedLines =
            renderText(
                maxWidth = maxWidth,
                renderedWidth = renderedWidth,
                align = effectiveAlign,
            )
        val rendered = terminal.render(renderedLines)

        // The trim sentinel is only injected on the plain-text path, so the markdown path needs no
        // strip at all, and plain lines without the sentinel are returned untouched (no per-line
        // String allocation from replace()).
        val strippedLines =
            if (markdown) {
                rendered.lines()
            } else {
                rendered.lines().map { line ->
                    if (line.indexOf(TRIM_SENTINEL) >= 0) line.replace(TRIM_SENTINEL_STRING, "") else line
                }
            }

        val lines =
            strippedLines
                .let { list ->
                    val effectiveMaxLines = maxLines ?: modifiedConstraints.maxHeight.takeIf { it != Int.MAX_VALUE }
                    if (effectiveMaxLines != null && list.size > effectiveMaxLines) {
                        when (overflow) {
                            TextOverflow.Clip -> list.take(effectiveMaxLines)
                            TextOverflow.Ellipsis -> {
                                val truncated = list.take(effectiveMaxLines).toMutableList()
                                if (truncated.isNotEmpty()) {
                                    val lastLine = truncated.last()
                                    truncated[truncated.lastIndex] =
                                        when {
                                            lastLine.length > 3 -> lastLine.dropLast(3) + "..."
                                            else -> "..."
                                        }
                                }
                                truncated
                            }
                            TextOverflow.Visible -> list
                        }
                    } else {
                        list
                    }
                }

        val width = modifiedConstraints.constrainWidth(renderedLines.width)
        val height = modifiedConstraints.constrainHeight(lines.size)

        return SimplePlaceable(
            width = width,
            height = height,
            lines = lines,
        )
    }

    private fun renderText(
        maxWidth: Int?,
        renderedWidth: Int,
        align: TextAlign,
    ): Lines {
        if (!markdown) {
            return renderPlainText(maxWidth = maxWidth, renderedWidth = renderedWidth, align = align)
        }

        // Memoize the parsed+rendered markdown. The output is a pure function of (terminal, text,
        // width, style): the terminal identity subsumes its capabilities (theme/ansi level/links),
        // which are fixed for a terminal's lifetime, so a cache hit can never serve bytes rendered
        // for a different terminal or width. Re-measures of unchanged markdown (relayout, scroll,
        // focus) skip the expensive AST parse entirely.
        val cacheKey = MarkdownCacheKey(terminal, text, renderedWidth, style)
        markdownLinesCache[cacheKey]?.let { return it }

        val markdownLines =
            try {
                Markdown(text).render(terminal, width = renderedWidth)
            } catch (_: Exception) {
                // Streaming LLM output can contain transient malformed markdown (e.g., unfinished fences).
                // Fall back to plain text so a parser failure can't crash the render loop.
                // The fallback is intentionally NOT cached.
                return renderPlainText(maxWidth = maxWidth, renderedWidth = renderedWidth, align = align)
            }

        val styled = style?.let { markdownLines.withBaseStyle(it) } ?: markdownLines
        markdownLinesCache[cacheKey] = styled
        return styled
    }

    private fun renderPlainText(
        maxWidth: Int?,
        renderedWidth: Int,
        align: TextAlign,
    ): Lines {
        val styledText = style?.invoke(text) ?: text
        val renderedText =
            styledText
                // Whitespace.PRE_WRAP trims whitespace at EOL; preserve trailing spaces by ensuring a
                // non-whitespace, zero-width sentinel is present at each explicit line end.
                .replace("\n", "$TRIM_SENTINEL\n") + TRIM_SENTINEL

        return MordantText(
            renderedText,
            whitespace = Whitespace.PRE_WRAP,
            align = align,
            overflowWrap = OverflowWrap.NORMAL,
            width = maxWidth,
        ).render(terminal, width = renderedWidth)
    }

    private data class MarkdownCacheKey(
        val terminal: com.github.ajalt.mordant.terminal.Terminal,
        val text: String,
        val width: Int,
        val style: TextStyle?,
    )

    private companion object {
        private const val MARKDOWN_CACHE_MAX = 128

        /**
         * Shared, bounded, access-ordered LRU of rendered markdown lines. Synchronized because the
         * frame/render loop may measure from more than one thread over the app's lifetime.
         */
        private val markdownLinesCache: MutableMap<MarkdownCacheKey, Lines> =
            java.util.Collections.synchronizedMap(
                object : LinkedHashMap<MarkdownCacheKey, Lines>(64, 0.75f, true) {
                    override fun removeEldestEntry(eldest: Map.Entry<MarkdownCacheKey, Lines>): Boolean =
                        size > MARKDOWN_CACHE_MAX
                },
            )

        init {
            // Process-global cache: register once and never unregister. On hibernation the runtime
            // clears it on the UI dispatcher, but other threads may still measure, so honour the
            // synchronizedMap contract by guarding the clear with the map's monitor.
            HibernationRegistry.registerReleaser {
                synchronized(markdownLinesCache) { markdownLinesCache.clear() }
            }
        }
        private const val TRIM_SENTINEL = '\u0000'

        private val TRIM_SENTINEL_STRING = TRIM_SENTINEL.toString()

        private fun Lines.withBaseStyle(style: TextStyle): Lines =
            Lines(
                lines.map { line ->
                    MordantLine(
                        spans = line.spans.map { span -> Span.word(span.text, style + span.style) },
                        endStyle = style + line.endStyle,
                    )
                },
            )
    }
}
