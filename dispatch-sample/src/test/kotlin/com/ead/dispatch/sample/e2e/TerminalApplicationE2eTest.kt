package com.ead.dispatch.sample.e2e

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("terminal-e2e")
@EnabledOnOs(OS.MAC, OS.LINUX)
@Timeout(60)
class TerminalApplicationE2eTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `installed chat streams and cancellation completes through a real PTY`() {
        PtyTerminalSession.start("chat-stream-and-cancel").use { terminal ->
            enterChat(terminal)

            sendMessage(terminal, "validate streaming")
            terminal.sendShiftTab()
            val secondStart = terminal.checkpoint()
            terminal.send("cancel this response")
            Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
            terminal.sendEnter()
            terminal.awaitText("Sample [streaming]", after = secondStart)
            Thread.sleep(100)
            val cancelStart = terminal.checkpoint()
            terminal.sendEscape()
            terminal.awaitText("[cancelled]", after = cancelStart)
            terminal.awaitQuiet(period = Duration.ofMillis(120))
        }
    }

    @Test
    fun `opening command palette does not append chat history to scrollback`() {
        PtyTerminalSession.start("command-palette-scrollback").use { terminal ->
            enterChat(terminal)
            repeat(3) { index ->
                sendMessage(terminal, "palette history ${index + 1}")
                terminal.sendShiftTab()
            }
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            terminal.send("/")
            terminal.awaitText("/clear")
            terminal.awaitQuiet()

            val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
            val appendWrites =
                diagnostics.lineSequence().filter {
                    it.contains("\"event\":\"terminal_write\"") &&
                        it.contains("\"operation\":\"append_scrolling\"")
                }.toList()
            val rewriteClearLineCounts =
                diagnostics.lineSequence()
                    .filter { it.contains("\"operation\":\"rewrite_viewport\"") }
                    .mapNotNull { Regex("\"clearLines\":(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
                    .toList()
            assertTrue(appendWrites.isEmpty(), "Palette opening appended scrolling content: $appendWrites")
            assertTrue(
                rewriteClearLineCounts.all { it <= 30 },
                "Palette rewrite replayed more rows than the terminal viewport: $rewriteClearLineCounts",
            )
            assertFalse(diagnostics.contains("\"clearScreen\":true"))
        }
    }

    @Test
    fun `cold and cleared chat commit new messages to native scrollback`() {
        PtyTerminalSession.start("cold-and-cleared-chat-scrollback").use { terminal ->
            enterChat(terminal)
            val coldStart = terminal.diagnosticCheckpoint()

            repeat(3) { index ->
                sendMessage(terminal, "cold history ${index + 1}")
                terminal.sendShiftTab()
            }

            val coldDiagnostics = terminal.diagnosticEvents(coldStart)
            assertTrue(
                appendScrollingWrites(coldDiagnostics) >= 3,
                "Cold chat did not append message history to native scrollback: $coldDiagnostics",
            )

            val clearStart = terminal.diagnosticCheckpoint()
            terminal.send("/")
            terminal.awaitText("/clear")
            terminal.sendEnter()
            terminal.awaitQuiet()
            val clearDiagnostics = terminal.diagnosticEvents(clearStart)
            assertTrue(
                clearDiagnostics.contains("\"reason\":\"scrolling_content_reset\""),
                "Clearing chat did not classify the scrolling-content reset: $clearDiagnostics",
            )
            assertTrue(
                clearDiagnostics.contains("\"clearScrollback\":true"),
                "Clearing chat left obsolete native scrollback in place: $clearDiagnostics",
            )
            assertFalse(clearDiagnostics.contains("\"clearScreen\":true"))

            val repopulateStart = terminal.diagnosticCheckpoint()
            repeat(2) { index ->
                sendMessage(terminal, "repopulated history ${index + 1}")
                terminal.sendShiftTab()
            }
            val repopulateDiagnostics = terminal.diagnosticEvents(repopulateStart)
            assertTrue(
                appendScrollingWrites(repopulateDiagnostics) >= 2,
                "Repopulated chat did not append new native scrollback: $repopulateDiagnostics",
            )
        }
    }

    @Test
    fun `text field owns arrows and tab transfers focus to arrow navigable actions`() {
        PtyTerminalSession.start("text-focus-contract").use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendDown()
            val navigationStart = terminal.checkpoint()
            terminal.sendEnter()
            terminal.awaitText("Controls widget gallery", after = navigationStart)

            terminal.send("one")
            terminal.awaitText("Name: one")
            terminal.sendUp()
            terminal.send("X")
            terminal.awaitText("Name: Xone")
            terminal.sendDown()
            terminal.send("Y")
            terminal.awaitText("Name: XoneY")

            terminal.sendTab()
            terminal.send("secret")
            terminal.awaitText("Password length: 6")
            assertFalse(terminal.transcript().contains("secret"), "Password value leaked into terminal output")

            terminal.sendTab()
            terminal.sendDown()
            terminal.sendEnter()
            terminal.awaitText("Count: 1")
            terminal.sendUp()
            terminal.sendEnter()
            terminal.awaitText("Count: 2")
        }
    }

    @Test
    fun `text editing never emits a blank screen rewrite`() {
        PtyTerminalSession.start("text-render-stability").use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendDown()
            terminal.sendEnter()
            terminal.awaitText("Controls widget gallery")
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            "render without blinking".forEach { character ->
                terminal.send(character.toString())
                Thread.sleep(18)
            }
            terminal.awaitText("Name: render without blinking")
            terminal.awaitQuiet()

            val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
            assertFalse(diagnostics.contains("\"clearScreen\":true"), "Text editing emitted a blanking clear-screen write")
            assertTrue(
                diagnostics.contains("\"operation\":\"rewrite_viewport\"") ||
                    diagnostics.contains("\"operation\":\"update_active_area\""),
            )
        }
    }

    @Test
    fun `global navigator reaches every sample screen through PTY keys`() {
        PtyTerminalSession.start("all-screen-navigation").use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            val destinations =
                listOf(
                    1 to "Simulated streaming chat",
                    2 to "Controls widget gallery",
                    3 to "Surfaces widget gallery",
                    4 to "Data widget gallery",
                    5 to "Progress widget gallery",
                    6 to "Lists widget gallery",
                    7 to "Review widget gallery",
                    8 to "Workflow widget gallery",
                    0 to "Dispatch UI Sample",
                )
            destinations.forEach { (downCount, title) ->
                val start = terminal.checkpoint()
                terminal.sendCtrlP()
                terminal.awaitText("Home — main menu", after = start)
                repeat(downCount) { terminal.sendDown() }
                terminal.sendEnter()
                terminal.awaitText(title, after = start, timeout = Duration.ofSeconds(12))
            }
        }
    }

    @Test
    fun `repeated navigation clears obsolete scrollback once per transition`() {
        PtyTerminalSession.start("navigation-scrollback-cleanup").use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            repeat(10) {
                val forwardStart = terminal.checkpoint()
                terminal.sendCtrlP()
                terminal.awaitText("Go to", after = forwardStart)
                repeat(2) { terminal.sendDown() }
                terminal.sendEnter()
                terminal.awaitText("Controls widget gallery", after = forwardStart)

                val backStart = terminal.checkpoint()
                terminal.sendEscape()
                terminal.awaitText("Dispatch UI Sample", after = backStart)
            }

            val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
            val transitionDecisions =
                diagnostics.lineSequence().count {
                    it.contains("\"event\":\"render_decision\"") && it.contains("\"reason\":\"screen_transition\"")
                }
            val transitionCleanups =
                diagnostics.lineSequence().count {
                    it.contains("\"event\":\"terminal_write\"") && it.contains("\"clearScrollback\":true")
                }
            assertTrue(transitionDecisions == 20, "Expected 20 screen transitions, got $transitionDecisions")
            assertTrue(transitionCleanups == 20, "Expected 20 scrollback cleanups, got $transitionCleanups")
            assertFalse(diagnostics.contains("\"clearScreen\":true"))
        }
    }

    @Test
    fun `animated gallery remains non blanking at compact and wide PTY sizes`() {
        listOf(80 to 24, 160 to 50).forEach { (columns, lines) ->
            PtyTerminalSession.start("progress-${columns}x$lines", columns = columns, lines = lines).use { terminal ->
                terminal.awaitText("Dispatch UI Sample")
                repeat(4) { terminal.sendDown() }
                terminal.sendEnter()
                terminal.awaitText("Progress widget gallery")
                val diagnosticsStart = terminal.diagnosticCheckpoint()
                Thread.sleep(700)
                val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
                assertTrue(diagnostics.contains("\"event\":\"render_frame\""))
                assertFalse(diagnostics.contains("\"clearScreen\":true"))
            }
        }
    }

    @Test
    fun `continuous shrink applies only the settled PTY size`() {
        PtyTerminalSession.start("continuous-shrink", columns = 160, lines = 50).use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            repeat(4) { terminal.sendDown() }
            terminal.sendEnter()
            terminal.awaitText("Progress widget gallery")
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            listOf(150 to 46, 130 to 40, 110 to 34, 90 to 28, 80 to 24).forEach { (columns, lines) ->
                terminal.resize(columns, lines)
                Thread.sleep(35)
            }

            terminal.awaitRawRegex(
                Regex("."),
                after = terminal.rawCheckpoint(),
                timeout = Duration.ofSeconds(3),
            )
            Thread.sleep(300)
            val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
            val resizeEvents = diagnostics.lineSequence().filter { it.contains("\"event\":\"resize_applied\"") }.toList()
            assertTrue(resizeEvents.size == 1, "Expected one settled resize, got ${resizeEvents.size}: $resizeEvents")
            assertTrue(resizeEvents.single().contains("\"width\":80"))
            assertTrue(resizeEvents.single().contains("\"height\":24"))
            assertFalse(diagnostics.contains("\"clearScreen\":true"))
            val waitingOffset = diagnostics.indexOf("\"event\":\"resize_waiting\"")
            val appliedOffset = diagnostics.indexOf("\"event\":\"resize_applied\"")
            assertTrue(waitingOffset >= 0 && appliedOffset > waitingOffset)
            val settlingDiagnostics = diagnostics.substring(waitingOffset, appliedOffset)
            assertFalse(
                settlingDiagnostics.contains("\"event\":\"terminal_write\""),
                "Animated frames wrote to the terminal while resize was unsettled: $settlingDiagnostics",
            )
        }
    }

    private fun enterChat(terminal: PtyTerminalSession) {
        terminal.awaitText("Dispatch UI Sample")
        val start = terminal.checkpoint()
        terminal.sendEnter()
        terminal.awaitText("Simulated streaming chat", after = start)
    }

    private fun sendMessage(terminal: PtyTerminalSession, text: String) {
        val start = terminal.checkpoint()
        terminal.send(text)
        terminal.awaitText(text, after = start)
        Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
        terminal.sendEnter()
        terminal.awaitText("Sample [streaming]", after = start)
        terminal.awaitAnyText(
            expected = listOf("network stream.", "chunks are still arriving.", "active in this sample."),
            after = start,
            timeout = Duration.ofSeconds(15),
        )
        terminal.awaitQuiet(period = Duration.ofMillis(120), timeout = Duration.ofSeconds(15))
    }

    private fun appendScrollingWrites(diagnostics: String): Int =
        diagnostics.lineSequence().count {
            it.contains("\"event\":\"terminal_write\"") &&
                it.contains("\"operation\":\"append_scrolling\"")
        }

    private companion object {
        const val PASTE_SUPPRESSION_SETTLE_MILLIS = 300L
    }
}
