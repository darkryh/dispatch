package com.ead.dispatch.render

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TerminalRendererTest {
    private fun createRenderer(): Pair<TerminalRenderer, TerminalRecorder> {
        val recorder =
            TerminalRecorder(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = 80,
                height = 24,
                supportsAnsiCursor = true,
            )
        val terminal = Terminal(terminalInterface = recorder)
        return TerminalRenderer(terminal) to recorder
    }

    // ========== Basic Functionality Tests ==========

    @Test
    fun `updateActiveArea skips identical lines`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("> "))
        val firstOutput = recorder.output()

        renderer.updateActiveArea(listOf("> "))
        val secondOutput = recorder.output()

        assertEquals(firstOutput, secondOutput)
    }

    @Test
    fun `clearScreen emits scrollback code when requested`() {
        val (renderer, recorder) = createRenderer()

        renderer.clearScreen(clearScrollback = true)

        assertTrue(recorder.output().contains(AnsiCodes.CLEAR_SCROLLBACK))
    }

    // ========== Atomic Rendering Tests ==========

    @Test
    fun `updateActiveArea uses single atomic output`() {
        val (renderer, recorder) = createRenderer()

        // First update
        renderer.updateActiveArea(listOf("Line 1", "Line 2"))
        val firstOutput = recorder.output()

        // Second update - different content
        renderer.updateActiveArea(listOf("New Line 1", "New Line 2"))
        val secondOutput = recorder.output()

        // The output should have grown (new content added)
        assertTrue(secondOutput.length > firstOutput.length)

        // The new output should contain both new lines
        val newContent = secondOutput.substring(firstOutput.length)
        assertTrue(newContent.contains("New Line 1"))
        assertTrue(newContent.contains("New Line 2"))
    }

    @Test
    fun `updateActiveArea handles growing line count`() {
        val (renderer, recorder) = createRenderer()

        // Start with 1 line
        renderer.updateActiveArea(listOf("Line 1"))
        val afterOne = recorder.output()

        // Grow to 3 lines
        renderer.updateActiveArea(listOf("Line 1", "Line 2", "Line 3"))
        val afterThree = recorder.output()

        assertTrue(afterThree.length > afterOne.length)
        assertTrue(afterThree.contains("Line 3"))
    }

    @Test
    fun `updateActiveArea handles shrinking line count`() {
        val (renderer, recorder) = createRenderer()

        // Start with 3 lines
        renderer.updateActiveArea(listOf("Line 1", "Line 2", "Line 3"))

        // Shrink to 1 line
        renderer.updateActiveArea(listOf("Only Line"))
        val output = recorder.output()

        // Should contain clear operations for extra lines
        assertTrue(output.contains(AnsiCodes.CLEAR_LINE))
        assertTrue(output.contains("Only Line"))
    }

    @Test
    fun `updateActiveArea handles empty to non-empty transition`() {
        val (renderer, recorder) = createRenderer()

        // Start with empty
        renderer.updateActiveArea(emptyList())
        val afterEmpty = recorder.output()

        // Add content
        renderer.updateActiveArea(listOf("New content"))
        val afterContent = recorder.output()

        assertTrue(afterContent.length > afterEmpty.length)
        assertTrue(afterContent.contains("New content"))
    }

    @Test
    fun `updateActiveArea handles non-empty to empty transition`() {
        val (renderer, recorder) = createRenderer()

        // Start with content
        renderer.updateActiveArea(listOf("Content"))

        // Clear to empty
        renderer.updateActiveArea(emptyList())
        val output = recorder.output()

        // Should have cleared the line
        assertTrue(output.contains(AnsiCodes.CLEAR_LINE))
    }

    @Test
    fun `updateActiveArea moves cursor up for multi-line updates`() {
        val (renderer, recorder) = createRenderer()

        // Initialize with 3 lines
        renderer.updateActiveArea(listOf("Line 1", "Line 2", "Line 3"))

        // Update with different content
        renderer.updateActiveArea(listOf("New 1", "New 2", "New 3"))
        val output = recorder.output()

        // Should contain moveUp command to go back to top of active area
        assertTrue(output.contains(AnsiCodes.moveUp(2)))
    }

    // ========== appendScrollingContent Tests ==========

    @Test
    fun `appendScrollingContent preserves active area`() {
        val (renderer, recorder) = createRenderer()

        // Set up active area
        renderer.updateActiveArea(listOf("> input"))
        val afterActive = recorder.output()

        // Append scrolling content
        renderer.appendScrollingContent(listOf("Message 1", "Message 2"))
        val afterScroll = recorder.output()

        // Should contain both scrolling content and restored active area
        val newContent = afterScroll.substring(afterActive.length)
        assertTrue(newContent.contains("Message 1"))
        assertTrue(newContent.contains("Message 2"))
        assertTrue(newContent.contains("> input"))
    }

    @Test
    fun `appendScrollingContent with no active area just prints content`() {
        val (renderer, recorder) = createRenderer()

        renderer.appendScrollingContent(listOf("Scroll line"))
        val output = recorder.output()

        assertTrue(output.contains("Scroll line"))
    }

    @Test
    fun `appendScrollingContent with empty lines does nothing`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("> "))
        val before = recorder.output()

        renderer.appendScrollingContent(emptyList())
        val after = recorder.output()

        assertEquals(before, after)
    }

    @Test
    fun `rewriteViewport rewrites visible frame without trailing newline`() {
        val (renderer, recorder) = createRenderer()

        renderer.rewriteViewport(
            scrollingLines = listOf("History 1", "History 2"),
            activeLines = listOf("> input", "status"),
        )

        val output = recorder.output()
        assertTrue(output.contains(AnsiCodes.CLEAR_SCREEN))
        assertTrue(output.contains(AnsiCodes.CLEAR_SCROLLBACK))
        assertTrue(output.contains("History 1"))
        assertTrue(output.contains("> input"))
        assertFalse(output.endsWith("\n"))
    }

    @Test
    fun `rewriteViewport seeds active area state for subsequent updates`() {
        val (renderer, recorder) = createRenderer()

        renderer.rewriteViewport(
            scrollingLines = listOf("History"),
            activeLines = listOf("> old"),
        )
        val before = recorder.output()

        renderer.updateActiveArea(listOf("> new"))
        val delta = recorder.output().removePrefix(before)

        assertTrue(delta.contains("> new"))
    }

    // ========== clearActiveArea Tests ==========

    @Test
    fun `clearActiveArea clears displayed content`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Line 1", "Line 2"))
        renderer.clearActiveArea()
        val output = recorder.output()

        assertTrue(output.contains(AnsiCodes.CLEAR_LINE))
    }

    @Test
    fun `clearActiveArea with no content does not emit output`() {
        val (renderer, recorder) = createRenderer()

        val before = recorder.output()
        renderer.clearActiveArea()
        val after = recorder.output()

        assertEquals(before, after)
    }

    @Test
    fun `clearActiveArea followed by updateActiveArea works correctly`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Old content"))
        renderer.clearActiveArea()
        renderer.updateActiveArea(listOf("New content"))

        val output = recorder.output()
        assertTrue(output.contains("New content"))
    }

    @Test
    fun `handoffToShellPrompt clears active area and ends on a new line`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Input", "Status", "Hint"))
        val before = recorder.output()

        renderer.handoffToShellPrompt()
        val delta = recorder.output().removePrefix(before)

        assertTrue(delta.contains(AnsiCodes.CLEAR_LINE))
        assertTrue(delta.endsWith("\n"))
    }

    @Test
    fun `handoffToShellPrompt resets active area state for next update`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Old input"))
        renderer.handoffToShellPrompt()
        val before = recorder.output()

        renderer.updateActiveArea(listOf("New input"))
        val delta = recorder.output().removePrefix(before)

        assertTrue(delta.contains("New input"))
        assertFalse(delta.contains(AnsiCodes.moveUp(1)))
    }

    // ========== Edge Cases ==========

    @Test
    fun `updateActiveArea handles single line`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Single line"))
        val output = recorder.output()

        assertTrue(output.contains("Single line"))
        assertFalse(output.contains("\n"))
    }

    @Test
    fun `updateActiveArea handles many lines`() {
        val (renderer, recorder) = createRenderer()

        val manyLines = (1..20).map { "Line $it" }
        renderer.updateActiveArea(manyLines)
        val output = recorder.output()

        assertTrue(output.contains("Line 1"))
        assertTrue(output.contains("Line 20"))
    }

    @Test
    fun `updateActiveArea handles lines with special characters`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Line with émojis 🎉", "Tab\there"))
        val output = recorder.output()

        assertTrue(output.contains("émojis"))
        assertTrue(output.contains("Tab"))
    }

    @Test
    fun `rapid updates produce correct final state`() {
        val (renderer, recorder) = createRenderer()

        // Simulate rapid typing
        renderer.updateActiveArea(listOf("> a"))
        renderer.updateActiveArea(listOf("> ab"))
        renderer.updateActiveArea(listOf("> abc"))
        renderer.updateActiveArea(listOf("> abcd"))

        val output = recorder.output()

        // Final state should include the last update
        assertTrue(output.contains("> abcd"))
    }

    @Test
    fun `updateActiveArea redraws sibling lines when one line changes`() {
        val (renderer, recorder) = createRenderer()

        renderer.updateActiveArea(listOf("Line A", "Line B"))
        val before = recorder.output()

        renderer.updateActiveArea(listOf("Line A*", "Line B"))
        val delta = recorder.output().removePrefix(before)

        val clearCount = delta.windowed(AnsiCodes.CLEAR_LINE.length).count { it == AnsiCodes.CLEAR_LINE }
        assertTrue(clearCount >= 2, "expected both active lines to be cleared/redrawn")
        assertTrue(delta.contains("Line A*"))
        assertTrue(delta.contains("Line B"))
    }
}
