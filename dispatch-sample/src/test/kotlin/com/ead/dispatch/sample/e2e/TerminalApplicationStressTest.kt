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
        PtyTerminalSession.start(
            scenario = "full-workflow-stress-300-messages",
            columns = 100,
            lines = 30,
            environment =
                mapOf(
                    "DISPATCH_SAMPLE_STREAM_MIN_DELAY_MS" to "1",
                    "DISPATCH_SAMPLE_STREAM_MAX_DELAY_MS" to "1",
                ),
        ).use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            val emptyApplicationHeap = collectPostGcHeap(terminal, "emptyApplication")
            terminal.sendEnter()
            terminal.awaitText("Simulated streaming chat")

            repeat(MESSAGE_COUNT) { index ->
                val message = "stress message ${index + 1}"
                val start = terminal.checkpoint()
                terminal.send(message)
                terminal.awaitText(message, after = start)
                Thread.sleep(PASTE_SUPPRESSION_SETTLE_MILLIS)
                terminal.sendEnter()
                terminal.awaitText("Sample [streaming]", after = start)
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
            terminal.sendEnter()
            terminal.awaitText("Dispatch UI Sample")
            val routeBaselineHeap = collectPostGcHeap(terminal, "routeBaseline")

            repeat(5) {
                for (destination in 2..8) {
                    val start = terminal.checkpoint()
                    terminal.sendCtrlP()
                    terminal.awaitText("Go to", after = start)
                    repeat(destination) { terminal.sendDown() }
                    terminal.sendEnter()
                    terminal.awaitAnyText(
                        expected = listOf("Dispatch UI Sample", "Simulated streaming chat", "widget gallery"),
                        after = start,
                        timeout = Duration.ofSeconds(12),
                    )
                    val backStart = terminal.checkpoint()
                    terminal.sendEscape()
                    terminal.awaitText("Dispatch UI Sample", after = backStart)
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

    private fun collectPostGcHeap(terminal: PtyTerminalSession, label: String): Long {
        terminal.requestGarbageCollection()
        Thread.sleep(650)
        val start = terminal.checkpoint()
        terminal.sendCtrlP()
        terminal.awaitText("Home — main menu", after = start)
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
    }
}
