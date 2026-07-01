package io.github.darkryh.dispatch.sample.e2e

import io.github.darkryh.dispatch.sample.navigation.CatalogDestination
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
            terminal.awaitText("streaming…", after = secondStart)
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
            terminal.awaitText("clear")
            terminal.awaitQuiet()

            val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
            val appendWrites =
                diagnostics
                    .lineSequence()
                    .filter {
                        it.contains("\"event\":\"terminal_write\"") &&
                            it.contains("\"operation\":\"append_scrolling\"")
                    }.toList()
            val rewriteClearLineCounts =
                diagnostics
                    .lineSequence()
                    .filter { it.contains("\"operation\":\"rewrite_viewport\"") }
                    .mapNotNull {
                        Regex("\"clearLines\":(\\d+)")
                            .find(it)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                    }.toList()
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
            terminal.awaitText("clear")
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
    fun `inputs screen owns typing and tab transfers focus while masking the password`() {
        PtyTerminalSession.start("inputs-focus-contract").use { terminal ->
            terminal.awaitText(HOME_TITLE)
            val navigationStart = terminal.checkpoint()
            // The launcher grid starts on the first card (Inputs), so Enter opens it directly.
            terminal.sendEnter()
            terminal.awaitText("$BANNER Inputs", after = navigationStart)
            terminal.awaitText("PasswordField — masked input", after = navigationStart)

            // The name field owns the keyboard; typed text is echoed inline and by the display-only renderer.
            val typedName = "Grace"
            val nameStart = terminal.checkpoint()
            terminal.send(typedName)
            terminal.awaitText(typedName, after = nameStart)

            // Tab transfers focus to the password field, which masks every character.
            terminal.sendTab()
            val secret = "Pa55phrase"
            terminal.send(secret)
            terminal.awaitText("●●●●●")
            assertFalse(terminal.transcript().contains(secret), "Password value leaked into terminal output")
            // The earlier name is still visible, proving focus actually moved off it.
            assertTrue(terminal.transcript().contains(typedName), "Name field value disappeared after Tab")
        }
    }

    @Test
    fun `text editing never emits a blank screen rewrite`() {
        PtyTerminalSession.start("text-render-stability").use { terminal ->
            terminal.awaitText(HOME_TITLE)
            val navigationStart = terminal.checkpoint()
            terminal.sendEnter()
            terminal.awaitText("$BANNER Inputs", after = navigationStart)
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            "render without blinking".forEach { character ->
                terminal.send(character.toString())
                Thread.sleep(18)
            }
            terminal.awaitText("render without blinking")
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
            terminal.awaitText(HOME_TITLE)
            // Palette index (number of Down presses from the reset top entry) to the screen title banner.
            val destinations =
                listOf(
                    1 to "Inputs",
                    2 to "Buttons & Selection",
                    3 to "Lists",
                    4 to "Tables & Grid",
                    5 to "Hierarchy & Command",
                    6 to "Checklist & Tasks",
                    7 to "Progress",
                    8 to "Surfaces & Dividers",
                    9 to "Layout",
                    10 to "Diff & Review",
                    11 to "Simulated streaming chat",
                    0 to HOME_TITLE,
                )
            destinations.forEach { (downCount, title) ->
                val start = terminal.checkpoint()
                terminal.sendCtrlP()
                awaitPaletteOpen(terminal, after = start)
                repeat(downCount) { terminal.sendDown() }
                terminal.sendEnter()
                // The "▌ " title banner only appears on the screen itself, never in the palette list,
                // so anchoring on it cannot accidentally match a palette entry of the same name.
                terminal.awaitText("$BANNER $title", after = start, timeout = Duration.ofSeconds(12))
            }
        }
    }

    @Test
    fun `repeated navigation clears obsolete scrollback once per transition`() {
        PtyTerminalSession.start("navigation-scrollback-cleanup").use { terminal ->
            terminal.awaitText(HOME_TITLE)
            val diagnosticsStart = terminal.diagnosticCheckpoint()

            repeat(10) {
                val forwardStart = terminal.checkpoint()
                // The launcher grid keeps the cursor on the first card (Inputs); Enter opens it.
                terminal.sendEnter()
                terminal.awaitText("$BANNER Inputs", after = forwardStart)

                val backStart = terminal.checkpoint()
                terminal.sendEscape()
                terminal.awaitText("$BANNER $HOME_TITLE", after = backStart)
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
    fun `navigating back on a short terminal wipes the previous screen leaving no residue`() {
        // lines=20 mirrors a short macOS Terminal window: the launcher grid and sub-screens are
        // taller than the viewport, so the terminal scrolls. Under scroll, the previous screen's
        // rows sit at physical offsets that an absolute-home repaint never re-addresses — the
        // ghost fragments seen on Terminal.app but never in a tall IDE pane (which never scrolls).
        // Every screen transition must therefore wipe the whole visible screen, not just the rows
        // the new frame happens to paint.
        PtyTerminalSession.start("navigation-residue-short", columns = 120, lines = 20).use { terminal ->
            terminal.awaitText(HOME_TITLE)

            repeat(3) {
                val enterStart = terminal.checkpoint()
                // Launcher cursor rests on the first card (Inputs); Enter opens it.
                terminal.sendEnter()
                terminal.awaitText("$BANNER Inputs", after = enterStart)

                val backStart = terminal.checkpoint()
                val diagnosticsStart = terminal.diagnosticCheckpoint()
                terminal.sendEscape()
                terminal.awaitText("$BANNER $HOME_TITLE", after = backStart)
                terminal.awaitQuiet()

                val diagnostics = terminal.diagnosticEvents(diagnosticsStart)
                val transitionWrites =
                    diagnostics
                        .lineSequence()
                        .filter {
                            it.contains("\"event\":\"terminal_write\"") &&
                                it.contains("\"operation\":\"rewrite_viewport\"") &&
                                it.contains("\"clearScrollback\":true")
                        }.toList()
                assertTrue(
                    transitionWrites.isNotEmpty(),
                    "Back navigation recorded no screen-transition rewrite: $diagnostics",
                )
                // The transition must clear the visible screen to its end so no previous-screen row
                // can survive at a scrolled offset...
                assertTrue(
                    transitionWrites.all { it.contains("\"clearToEnd\":true") },
                    "Screen-transition rewrite did not wipe the visible screen — previous-screen rows can ghost: $transitionWrites",
                )
                // ...yet it must NOT use a full ESC[2J blanking clear-screen (that flickers and the
                // inline scrollback model is deliberately preserved).
                assertFalse(
                    diagnostics.contains("\"clearScreen\":true"),
                    "Transition emitted a blanking clear-screen instead of an in-place wipe: $diagnostics",
                )
            }
        }
    }

    @Test
    fun `animated gallery remains non blanking at compact and wide PTY sizes`() {
        listOf(80 to 24, 160 to 50).forEach { (columns, lines) ->
            PtyTerminalSession.start("progress-${columns}x$lines", columns = columns, lines = lines).use { terminal ->
                terminal.awaitText(HOME_TITLE)
                openProgress(terminal)
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
            terminal.awaitText(HOME_TITLE)
            openProgress(terminal)
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
        terminal.awaitText(HOME_TITLE)
        val start = terminal.checkpoint()
        // Chat is the last palette entry (index 11 after the reset-to-top "Home").
        terminal.sendCtrlP()
        awaitPaletteOpen(terminal, after = start)
        repeat(11) { terminal.sendDown() }
        terminal.sendEnter()
        terminal.awaitText("$BANNER Simulated streaming chat", after = start)
    }

    /**
     * Waits until the global "go to…" palette is open. The palette's panel title can scroll off the
     * top of a small viewport when it overflows the launcher grid, so this gates on the always-visible
     * last entry (Chat) instead — a line only the palette ever renders.
     */
    private fun awaitPaletteOpen(
        terminal: PtyTerminalSession,
        after: Int,
    ) {
        terminal.awaitText("Chat — Live streaming", after = after)
    }

    private fun openProgress(terminal: PtyTerminalSession) {
        val start = terminal.checkpoint()
        // Reach Progress through the command palette — the same route the all-screen navigator proves
        // reliable on slow CI runners. Driving the 2-D launcher grid directly (Down + Right) raced
        // here: on a loaded runner the movement keys were dropped before the grid became interactive,
        // so Enter opened the default Inputs card and the wait below timed out. The palette is a 1-D
        // list gated on being open, so every keystroke lands. Progress sits ordinal + 1 Down presses
        // in (index 0 resets to Home), computed from the enum so a reorder can't silently break it.
        terminal.sendCtrlP()
        awaitPaletteOpen(terminal, after = start)
        repeat(CatalogDestination.PROGRESS.ordinal + 1) { terminal.sendDown() }
        terminal.sendEnter()
        terminal.awaitText("$BANNER Progress", after = start, timeout = Duration.ofSeconds(12))
    }

    private fun sendMessage(
        terminal: PtyTerminalSession,
        text: String,
    ) {
        val start = terminal.checkpoint()
        terminal.send(text)
        terminal.awaitText(text, after = start)
        Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
        terminal.sendEnter()
        terminal.awaitText("streaming…", after = start)
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

        /** The Home launcher title, used both as the start gate and the Home destination. */
        const val HOME_TITLE = "Dispatch — Terminal UI Showcase"

        /** The left-bar glyph the shared TitleBanner prefixes every screen title with. */
        const val BANNER = "▌"
    }
}
