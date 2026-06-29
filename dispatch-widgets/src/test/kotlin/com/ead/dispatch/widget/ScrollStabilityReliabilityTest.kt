package com.ead.dispatch.widget

import androidx.compose.runtime.Composable
import com.ead.dispatch.layout.Column
import com.ead.dispatch.layout.Row
import com.ead.dispatch.layout.Spacer
import com.ead.dispatch.modifier.Modifier
import com.ead.dispatch.modifier.fillMaxWidth
import com.ead.dispatch.modifier.height
import com.ead.dispatch.modifier.width
import com.ead.dispatch.widget.harness.ReliabilityHarness
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reliability tests for verifying scrolling content stability when widgets appear/disappear.
 *
 * Bug scenario: When animation widget or command palette appears/disappears,
 * the history content gets cut off and only visible content remains.
 *
 * These tests verify that ALL messages remain in the rendered output regardless
 * of transient widgets appearing/disappearing.
 */
class ScrollStabilityReliabilityTest {
    private val harness = ReliabilityHarness(width = 80, height = 24)

    /**
     * Render with BOUNDED height like the real application does.
     * The real DispatchApplication uses unconstrained height for measurement,
     * but we use bounded here to test the virtualized rendering behavior.
     */
    private fun renderWithBoundedHeight(content: @Composable () -> Unit): List<String> =
        harness.render(boundedHeight = true, content = content)

    /**
     * Render with UNBOUNDED height to test the scrollback splitting logic.
     * This is closer to how the real DispatchApplication works.
     */
    private fun renderWithUnboundedHeight(content: @Composable () -> Unit): List<String> =
        harness.render(boundedHeight = false, content = content)

    @Test
    fun `animation appearing does not cut off scrolling history`() {
        // Step 1: Render without animation
        val linesWithoutAnimation =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(
                    messageCount = 5,
                    isProcessing = false,
                )
            }

        // Verify we got the full history - search for "message X" not "msgX"
        assertTrue(linesWithoutAnimation.any { it.contains("message 0") }, "Message 0 should be visible")
        assertTrue(linesWithoutAnimation.any { it.contains("message 4") }, "Message 4 should be visible")

        // Step 2: Render WITH animation appearing
        val linesWithAnimation =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(
                    messageCount = 5,
                    isProcessing = true, // Animation appears!
                )
            }

        // THE BUG: With animation, message 0 might get cut off
        assertTrue(
            linesWithAnimation.any { it.contains("message 0") },
            "Message 0 should still be visible after animation appears - BUG IF THIS FAILS!",
        )

        // Verify all messages are still in the output
        for (i in 0 until 5) {
            val found = linesWithAnimation.any { it.contains("message $i") }
            assertTrue(found, "Message $i should be in output after animation appeared")
        }
    }

    @Test
    fun `animation disappearing does not cut off scrolling history`() {
        // Step 1: Render WITH animation
        val linesWithAnimation =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(
                    messageCount = 5,
                    isProcessing = true,
                )
            }

        // Verify all messages present
        for (i in 0 until 5) {
            assertTrue(linesWithAnimation.any { it.contains("message $i") }, "Message $i should be visible with animation")
        }

        // Step 2: Render WITHOUT animation (animation disappears)
        val linesWithoutAnimation =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(
                    messageCount = 5,
                    isProcessing = false, // Animation disappears!
                )
            }

        // Verify message 0 is still visible
        assertTrue(
            linesWithoutAnimation.any { it.contains("message 0") },
            "Message 0 should be visible after animation disappears - BUG IF THIS FAILS!",
        )

        // Verify all messages are present
        for (i in 0 until 5) {
            val found = linesWithoutAnimation.any { it.contains("message $i") }
            assertTrue(found, "Message $i should be in output after animation disappeared")
        }
    }

    @Test
    fun `command palette appearing does not cut off scrolling history`() {
        // Step 1: Render without command palette
        val linesWithoutPalette =
            renderWithBoundedHeight {
                ChatScreenWithCommandPalette(
                    messageCount = 5,
                    commandPaletteVisible = false,
                    inputValue = "hello",
                )
            }

        // Verify all messages present
        for (i in 0 until 5) {
            assertTrue(linesWithoutPalette.any { it.contains("message $i") }, "Message $i should be visible without palette")
        }

        // Step 2: Render WITH command palette (triggered by /)
        val linesWithPalette =
            renderWithBoundedHeight {
                ChatScreenWithCommandPalette(
                    messageCount = 5,
                    commandPaletteVisible = true, // Palette appears!
                    inputValue = "/",
                )
            }

        // Verify message 0 is still visible
        assertTrue(
            linesWithPalette.any { it.contains("message 0") },
            "Message 0 should be visible after command palette appears - BUG IF THIS FAILS!",
        )

        // Verify all messages are present
        for (i in 0 until 5) {
            val found = linesWithPalette.any { it.contains("message $i") }
            assertTrue(found, "Message $i should be in output after palette appeared")
        }
    }

    @Test
    fun `command palette disappearing does not cut off scrolling history`() {
        // Step 1: Render WITH command palette
        val linesWithPalette =
            renderWithBoundedHeight {
                ChatScreenWithCommandPalette(
                    messageCount = 5,
                    commandPaletteVisible = true,
                    inputValue = "/",
                )
            }

        // Verify all messages present
        for (i in 0 until 5) {
            assertTrue(linesWithPalette.any { it.contains("message $i") }, "Message $i should be visible with palette")
        }

        // Step 2: Render WITHOUT command palette (user typed space after /)
        val linesWithoutPalette =
            renderWithBoundedHeight {
                ChatScreenWithCommandPalette(
                    messageCount = 5,
                    commandPaletteVisible = false, // Palette disappears!
                    inputValue = "/ ",
                )
            }

        // Verify all messages are still present
        for (i in 0 until 5) {
            val found = linesWithoutPalette.any { it.contains("message $i") }
            assertTrue(found, "Message $i should be in output after palette disappeared")
        }
    }

    @Test
    fun `multiple toggle cycles maintain scrolling stability`() {
        // Cycle 1: animation appears
        var lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(5, isProcessing = true, commandPaletteVisible = false)
            }
        assertAllMessagesPresent(lines, 5, "cycle 1 (animation on)")

        // Cycle 2: animation disappears
        lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(5, isProcessing = false, commandPaletteVisible = false)
            }
        assertAllMessagesPresent(lines, 5, "cycle 2 (animation off)")

        // Cycle 3: animation appears again
        lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(5, isProcessing = true, commandPaletteVisible = false)
            }
        assertAllMessagesPresent(lines, 5, "cycle 3 (animation on again)")

        // Cycle 4: command palette appears
        lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(5, isProcessing = true, commandPaletteVisible = true)
            }
        assertAllMessagesPresent(lines, 5, "cycle 4 (palette on)")

        // Cycle 5: both disappear
        lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(5, isProcessing = false, commandPaletteVisible = false)
            }
        assertAllMessagesPresent(lines, 5, "cycle 5 (all off)")
    }

    @Test
    fun `rapid animation toggle does not lose history`() {
        // Simulates rapid on/off of processing indicator
        repeat(10) { iteration ->
            val isProcessing = iteration % 2 == 0

            val lines =
                renderWithBoundedHeight {
                    ChatScreenWithAnimation(
                        messageCount = 5,
                        isProcessing = isProcessing,
                    )
                }

            // Each iteration should still have all messages visible
            for (i in 0 until 5) {
                val found = lines.any { it.contains("message $i") }
                assertTrue(found, "Message $i should be visible at iteration $iteration")
            }
        }
    }

    @Test
    fun `history intact with both animation and palette toggling`() {
        val messageCount = 10

        // Various states
        val states =
            listOf(
                Triple(false, false, "both off"),
                Triple(true, false, "animation on"),
                Triple(false, true, "palette on"),
                Triple(true, true, "both on"),
                Triple(false, false, "both off again"),
                Triple(true, false, "animation on final"),
            )

        for ((isProcessing, paletteVisible, stateName) in states) {
            val lines =
                renderWithBoundedHeight {
                    ChatScreenWithAnimation(
                        messageCount = messageCount,
                        isProcessing = isProcessing,
                        commandPaletteVisible = paletteVisible,
                    )
                }
            assertAllMessagesPresent(lines, messageCount, stateName)
        }
    }

    /**
     * Test with UNBOUNDED height - this tests the actual scrollback splitting logic.
     * This is where the bug manifests when widgets appear/disappear.
     */
    @Test
    fun `unbounded render preserves all messages when animation toggles`() {
        // With unbounded height, all content should be rendered
        // This tests the ScrollableList.measureUnbounded path

        // Initial: without animation
        val linesWithoutAnimation =
            renderWithUnboundedHeight {
                ChatScreenWithAnimation(messageCount = 10, isProcessing = false)
            }

        // Should have all 10 messages + header + input + status
        assertTrue(linesWithoutAnimation.any { it.contains("message 0") }, "message 0 should be in output")
        assertTrue(linesWithoutAnimation.any { it.contains("message 9") }, "message 9 should be in output")

        // With animation
        val linesWithAnimation =
            renderWithUnboundedHeight {
                ChatScreenWithAnimation(messageCount = 10, isProcessing = true)
            }

        // All messages should still be present
        assertTrue(linesWithAnimation.any { it.contains("message 0") }, "message 0 should be in output with animation")
        assertTrue(linesWithAnimation.any { it.contains("message 9") }, "message 9 should be in output with animation")
    }

    /**
     * This test demonstrates the actual bug: when using bounded height (virtualization),
     * older messages get dropped because virtualization only renders visible content.
     *
     * This is expected behavior with bounded viewport, but the user's issue is that
     * even with unbounded measurement, the scrollback splitting logic loses content
     * when widgets appear/disappear.
     *
     * The fix would be to ensure the scrollState.offset is properly adjusted when
     * widget height changes.
     */
    @Test
    fun `verify virtualization behavior with bounded height`() {
        // With bounded height (24 lines), only a portion of messages are visible
        // This is expected - the rest are virtualized away
        val lines =
            renderWithBoundedHeight {
                ChatScreenWithAnimation(messageCount = 20, isProcessing = false)
            }

        // With 20 messages (2 lines each = 40 lines) + header + input + status
        // Only ~24 lines visible at a time
        val visibleMessages =
            (0 until 20).filter { i ->
                lines.any { it.contains("message $i") }
            }

        // Should see some but not all messages (due to virtualization)
        assertTrue(visibleMessages.isNotEmpty(), "Should see some messages")
        assertTrue(visibleMessages.size < 20, "Should NOT see all messages due to virtualization")
    }

    private fun assertAllMessagesPresent(
        lines: List<String>,
        count: Int,
        context: String,
    ) {
        for (i in 0 until count) {
            val found = lines.any { it.contains("message $i") }
            assertTrue(found, "Message $i should be visible - $context")
        }
    }
}

/**
 * Chat screen simulation with animation (processing spinner)
 */
@Composable
private fun ChatScreenWithAnimation(
    messageCount: Int,
    isProcessing: Boolean,
    commandPaletteVisible: Boolean = false,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Header
        item { Text("=== Chat ===") }
        item { Spacer(Modifier.height(1)) }

        // Messages
        items((0 until messageCount).toList()) { index ->
            Text(text = "user: message $index")
            Text(text = "  assistant: response $index")
        }

        // Progress animation - appears when isProcessing is true
        if (isProcessing) {
            item {
                Column {
                    Row {
                        Spacer(Modifier.width(2))
                        Spinner(frame = 0, style = SpinnerStyle.Dots)
                        Spacer(Modifier.width(1))
                        Text(text = "let him cook")
                    }
                    Spacer(Modifier.height(1))
                }
            }
        }

        // Input field
        item {
            TextField(
                value = "",
                onValueChange = {},
                icon = "> ",
                placeholder = "Type a message...",
            )
        }

        // Command palette
        if (commandPaletteVisible) {
            item {
                CommandPalette(
                    options =
                        listOf(
                            CommandOption(label = "clear", description = "Clear", data = "clear"),
                            CommandOption(label = "help", description = "Help", data = "help"),
                        ),
                    inputValue = "/",
                    onOptionSelected = {},
                    onInputTransform = {},
                    textStyles = CommandPaletteTextStyles(),
                )
            }
        }

        // Status bar
        item { Text("Status: ready") }
    }
}

/**
 * Chat screen simulation with command palette
 */
@Composable
private fun ChatScreenWithCommandPalette(
    messageCount: Int,
    commandPaletteVisible: Boolean,
    inputValue: String,
) {
    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        // Header
        item { Text("=== Chat ===") }
        item { Spacer(Modifier.height(1)) }

        // Messages
        items((0 until messageCount).toList()) { index ->
            Text(text = "user: message $index")
            Text(text = "  assistant: response $index")
        }

        // Input field
        item {
            TextField(
                value = inputValue,
                onValueChange = {},
                icon = "> ",
                placeholder = "Type a message...",
            )
        }

        // Command palette
        if (commandPaletteVisible) {
            item {
                CommandPalette(
                    options =
                        listOf(
                            CommandOption(label = "clear", description = "Clear", data = "clear"),
                            CommandOption(label = "help", description = "Help", data = "help"),
                        ),
                    inputValue = inputValue,
                    onOptionSelected = {},
                    onInputTransform = {},
                    textStyles = CommandPaletteTextStyles(),
                )
            }
        }

        // Status bar
        item { Text("Status: ready") }
    }
}
