package io.github.darkryh.dispatch.sample.e2e

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
import kotlin.test.assertTrue

@Tag("terminal-stress")
@EnabledOnOs(OS.MAC, OS.LINUX)
@Timeout(1_800)
class TerminalApplicationStressTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `three hundred messages and repeated all screen navigation remain bounded`() {
        PtyTerminalSession
            .start(
                scenario = "full-workflow-stress-300-messages",
                columns = 100,
                lines = 30,
                environment =
                    mapOf(
                        "DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS" to "1",
                        "DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS" to "1",
                    ),
            ).use { terminal ->
                terminal.awaitText(HOME_TITLE)
                val emptyApplicationHeap = collectPostGcHeap(terminal, "emptyApplication")
                enterChat(terminal)

                repeat(MESSAGE_COUNT) { index ->
                    val message = "stress message ${index + 1}"
                    val start = terminal.checkpoint()
                    terminal.send(message)
                    terminal.awaitText(message, after = start)
                    Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
                    terminal.sendEnter()
                    terminal.awaitText("streaming…", after = start)
                    terminal.awaitAnyText(
                        expected = listOf("network stream.", "chunks are still arriving.", "active in this sample."),
                        after = start,
                        timeout = Duration.ofSeconds(15),
                    )
                    terminal.awaitQuiet(period = Duration.ofMillis(120), timeout = Duration.ofSeconds(15))
                    if ((index + 1) % MEMORY_CHECKPOINT_INTERVAL == 0) {
                        terminal.latestHeapUsedBytes()?.let { heap ->
                            terminal.recordMeasurement("heapAfter${index + 1}MessagesBytes", heap)
                        }
                    }
                    if (index < MESSAGE_COUNT - 1) terminal.sendShiftTab()
                }

                val populatedConversationHeap = collectPostGcHeap(terminal, "populatedConversation")
                terminal.recordMeasurement(
                    "retainedConversationHeapGrowthBytes",
                    populatedConversationHeap - emptyApplicationHeap,
                )
                val homeStart = terminal.checkpoint()
                terminal.sendEscape()
                terminal.awaitText("$BANNER $HOME_TITLE", after = homeStart)
                val routeBaselineHeap = collectPostGcHeap(terminal, "routeBaseline")

                repeat(5) {
                    for (destination in ROUTE_TITLES.keys) {
                        val start = terminal.checkpoint()
                        terminal.sendCtrlP()
                        awaitPaletteOpen(terminal, after = start)
                        repeat(destination) { terminal.sendDown() }
                        terminal.sendEnter()
                        terminal.awaitText(
                            "$BANNER ${ROUTE_TITLES.getValue(destination)}",
                            after = start,
                            timeout = Duration.ofSeconds(12),
                        )
                        val backStart = terminal.checkpoint()
                        terminal.sendEscape()
                        terminal.awaitText("$BANNER $HOME_TITLE", after = backStart)
                    }
                }

                val finalHeap = collectPostGcHeap(terminal, "final")
                val diagnostics = terminal.diagnosticEvents()
                val heapSamples = Regex("\"heapUsedBytes\":(\\d+)").findAll(diagnostics).map { it.groupValues[1].toLong() }.toList()
                assertTrue(heapSamples.size >= 3, "Expected periodic in-process heap samples")
                assertTrue(diagnostics.contains("\"event\":\"render_frame\""))
                assertTrue(diagnostics.contains("\"event\":\"terminal_write\""))
                assertTrue(
                    finalHeap <= routeBaselineHeap + MAX_RETAINED_HEAP_GROWTH_BYTES,
                    "Retained heap grew from $routeBaselineHeap to $finalHeap after bounded route churn",
                )
            }
    }

    private fun enterChat(terminal: PtyTerminalSession) {
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
     * last entry (Chat) — a line only the palette ever renders.
     */
    private fun awaitPaletteOpen(
        terminal: PtyTerminalSession,
        after: Int,
    ) {
        terminal.awaitText("Chat — Live streaming", after = after)
    }

    private fun collectPostGcHeap(
        terminal: PtyTerminalSession,
        label: String,
    ): Long {
        terminal.requestGarbageCollection()
        Thread.sleep(650)
        val start = terminal.checkpoint()
        terminal.sendCtrlP()
        awaitPaletteOpen(terminal, after = start)
        terminal.sendEscape()
        terminal.awaitQuiet(period = Duration.ofMillis(150), timeout = Duration.ofSeconds(3))
        val heap = requireNotNull(terminal.latestHeapUsedBytes()) { "Missing post-GC heap sample for $label" }
        terminal.recordMeasurement("${label}HeapUsedBytes", heap)
        return heap
    }

    private companion object {
        const val MESSAGE_COUNT = 300
        const val MEMORY_CHECKPOINT_INTERVAL = 100
        const val PASTE_SUPPRESSION_SETTLE_MILLIS = 180L
        const val MAX_RETAINED_HEAP_GROWTH_BYTES = 20L * 1024 * 1024

        /** The Home launcher title and the left-bar glyph the shared TitleBanner prefixes titles with. */
        const val HOME_TITLE = "Dispatch — Terminal UI Showcase"
        const val BANNER = "▌"

        /** Palette index (Down presses from the reset top entry) to the destination screen title. */
        val ROUTE_TITLES =
            linkedMapOf(
                2 to "Buttons & Selection",
                3 to "Lists",
                4 to "Tables & Grid",
                5 to "Hierarchy & Command",
                6 to "Checklist & Tasks",
                7 to "Progress",
                8 to "Surfaces & Dividers",
            )
    }
}
