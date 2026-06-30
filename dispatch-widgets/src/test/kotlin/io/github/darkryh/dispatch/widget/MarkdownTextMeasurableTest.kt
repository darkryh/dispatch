package io.github.darkryh.dispatch.widget

import io.github.darkryh.dispatch.constraints.Constraints
import io.github.darkryh.dispatch.modifier.Modifier
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.TextAlign
import com.github.ajalt.mordant.terminal.Terminal
import kotlin.test.Test
import kotlin.test.assertTrue

class MarkdownTextMeasurableTest {
    private fun terminal(
        width: Int = 40,
        height: Int = 20,
    ): Terminal =
        Terminal(
            ansiLevel = AnsiLevel.TRUECOLOR,
            width = width,
            height = height,
            interactive = false,
            hyperlinks = false,
        )

    private fun stripAnsi(text: String): String {
        // CSI (ESC[...<final>), OSC (ESC]...BEL or ESC\), or 2-byte escapes (ESC<ch>)
        val regex = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\].*?(?:\u0007|\u001B\\\\)|\u001B.")
        return text.replace(regex, "")
    }

    private fun renderMarkdown(
        markdown: String,
        width: Int = 60,
        height: Int = 40,
    ): String {
        val measurable =
            TextMeasurable(
                text = markdown,
                modifier = Modifier,
                style = null,
                align = TextAlign.LEFT,
                maxLines = null,
                overflow = TextOverflow.Clip,
                markdown = true,
                terminal = terminal(width = width, height = height),
            )

        return measurable
            .measure(Constraints(maxWidth = width, maxHeight = height))
            .lines
            .joinToString("\n") { stripAnsi(it) }
    }

    @Test
    fun `h3 headings are centered by Mordant`() {
        val rendered = renderMarkdown("### Key Reasons", width = 40, height = 20)
        val line = rendered.lineSequence().first { it.contains("Key Reasons") }
        val firstChar = line.indexOfFirst { it != ' ' }

        // With the default theme, h3 uses a HorizontalRule with a space "rule" and centered title.
        assertTrue(firstChar > 1, "expected heading to have leading spaces (centered), got: '$line'")
    }

    @Test
    fun `unordered lists render bullet characters`() {
        val rendered = renderMarkdown("- A\n- B", width = 30, height = 20)

        assertTrue(rendered.contains("• A"))
        assertTrue(rendered.contains("• B"))
        assertTrue(!rendered.contains("- A"))
        assertTrue(!rendered.contains("- B"))
    }

    @Test
    fun `code fences render without backticks`() {
        val rendered = renderMarkdown("```kotlin\nval x = 1\n```", width = 30, height = 20)

        assertTrue(rendered.contains("val x = 1"))
        assertTrue(!rendered.contains("```"))
    }

    @Test
    fun `block quotes render without delimiter`() {
        val rendered = renderMarkdown("> quoted text", width = 30, height = 20)

        assertTrue(rendered.contains("quoted text"))
        assertTrue(rendered.contains("▎"), "expected block quote bar to be rendered")
        assertTrue(!rendered.contains("> quoted text"))
    }

    @Test
    fun `inline code renders without backticks`() {
        val rendered = renderMarkdown("Use `code` here.", width = 30, height = 20)

        assertTrue(rendered.contains("Use"))
        assertTrue(rendered.contains("code"))
        assertTrue(!rendered.contains("`code`"))
    }

    @Test
    fun `links render text and destination when hyperlinks are disabled`() {
        val rendered = renderMarkdown("[site](https://example.com)", width = 50, height = 20)

        assertTrue(rendered.contains("site"))
        assertTrue(rendered.contains("https://example.com"))
        assertTrue(!rendered.contains("[site]"))
    }

    @Test
    fun `ordered lists number items`() {
        val rendered =
            renderMarkdown(
                """
1. first
1. second
1. third
""".trim(),
                width = 30,
                height = 20,
            )

        assertTrue(rendered.contains("1. first"))
        assertTrue(rendered.contains("2. second"))
        assertTrue(rendered.contains("3. third"))
    }

    @Test
    fun `nested lists indent children`() {
        val rendered =
            renderMarkdown(
                """
- parent
  - child
""".trim(),
                width = 30,
                height = 20,
            )

        val lines = rendered.lineSequence().toList()
        val parentLine = lines.first { it.contains("parent") }
        val childLine = lines.first { it.contains("child") }

        val parentBulletColumn = parentLine.indexOf('•')
        val childBulletColumn = childLine.indexOf('•')

        assertTrue(parentBulletColumn >= 0, "expected parent bullet to be rendered, got: '$parentLine'")
        assertTrue(childBulletColumn > parentBulletColumn, "expected child bullet to be indented, got: '$childLine'")
    }

    @Test
    fun `task lists render checkboxes`() {
        val rendered =
            renderMarkdown(
                """
- [ ] todo
- [x] done
""".trim(),
                width = 30,
                height = 20,
            )

        assertTrue(rendered.contains("☐ todo"))
        assertTrue(rendered.contains("☑ done"))
        assertTrue(!rendered.contains("[ ] todo"))
        assertTrue(!rendered.contains("[x] done"))
    }

    @Test
    fun `tables render grid`() {
        val rendered =
            renderMarkdown(
                """
| a | b |
|---|---|
| 1 | 2 |
""".trim(),
                width = 60,
                height = 20,
            )

        assertTrue(rendered.contains("a"))
        assertTrue(rendered.contains("b"))
        assertTrue(rendered.contains("1"))
        assertTrue(rendered.contains("2"))
        assertTrue(rendered.contains("┌") && rendered.contains("┐") && rendered.contains("│"))
    }

    @Test
    fun `images render as an icon with alt text`() {
        val rendered =
            renderMarkdown(
                "![an image](example.png)",
                width = 40,
                height = 10,
            )

        assertTrue(rendered.contains("🖼️"))
        assertTrue(rendered.contains("an image"))
        assertTrue(!rendered.contains("![an image]"))
    }

    @Test
    fun `autolinks render without markdown brackets`() {
        val rendered =
            renderMarkdown(
                "<https://example.com/autolink>",
                width = 60,
                height = 10,
            )

        assertTrue(rendered.contains("https://example.com/autolink"))
        assertTrue(!rendered.contains("[https://example.com/autolink]"))
    }

    @Test
    fun `inline html tags are skipped by default`() {
        val rendered =
            renderMarkdown(
                "<b>bold</b>",
                width = 20,
                height = 10,
            )

        assertTrue(rendered.contains("bold"))
        assertTrue(!rendered.contains("<b>"))
        assertTrue(!rendered.contains("</b>"))
    }

    @Test
    fun `inline math renders without dollar delimiters`() {
        val rendered =
            renderMarkdown(
                "Math: $\\sqrt{4}$.",
                width = 40,
                height = 10,
            )

        assertTrue(rendered.contains("\\sqrt{4}"))
        assertTrue(!rendered.contains("$\\sqrt{4}$"))
    }

    @Test
    fun `strikethrough renders without tildes`() {
        val rendered =
            renderMarkdown(
                "~~gone~~",
                width = 20,
                height = 10,
            )

        assertTrue(rendered.contains("gone"))
        assertTrue(!rendered.contains("~~gone~~"))
    }

    @Test
    fun `malformed streaming markdown does not throw`() {
        val rendered =
            renderMarkdown(
                """
What I can offer right now:

 1. The exact command you'd need to create the character when you have write access
 2. A fully developed character sheet ready for creation
 3. Suggestions for your next message that might trigger write permissions

**For example, here's the exact character data structure you could use
""".trim(),
                width = 80,
                height = 40,
            )

        assertTrue(rendered.contains("What I can offer right now"))
        assertTrue(rendered.contains("For example"))
    }

    @Test
    fun `unterminated code fence does not crash renderer`() {
        val rendered =
            renderMarkdown(
                """
Here is a JSON payload:
```json
{
  "name": "Cassian"
}
""".trim(),
                width = 80,
                height = 40,
            )

        assertTrue(rendered.contains("Here is a JSON payload"))
        assertTrue(rendered.contains("Cassian"))
    }

    @Test
    fun `bare fence opener does not crash renderer`() {
        val rendered =
            renderMarkdown(
                "```",
                width = 40,
                height = 10,
            )

        assertTrue(rendered.isNotEmpty())
    }
}
