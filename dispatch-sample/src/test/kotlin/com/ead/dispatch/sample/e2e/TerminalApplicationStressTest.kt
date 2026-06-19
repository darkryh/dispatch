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
@Timeout(240)
class TerminalApplicationStressTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `ten messages and repeated all screen navigation remain bounded`() {
        PtyTerminalSession.start("full-workflow-stress", columns = 100, lines = 30).use { terminal ->
            terminal.awaitText("Dispatch UI Sample")
            terminal.sendEnter()
            terminal.awaitText("Simulated streaming chat")

            repeat(10) { index ->
                val message = "stress message ${index + 1}"
                val start = terminal.checkpoint()
                terminal.send(message)
                terminal.awaitText(message, after = start)
                Thread.sleep(300)
                terminal.sendEnter()
                terminal.awaitText("Sample [streaming]", after = start)
                terminal.awaitAnyText(
                    expected = listOf("network stream.", "chunks are still arriving.", "active in this sample."),
                    after = start,
                    timeout = Duration.ofSeconds(15),
                )
                terminal.awaitQuiet(period = Duration.ofMillis(120), timeout = Duration.ofSeconds(15))
                if (index < 9) terminal.sendShiftTab()
            }

            terminal.sendEnter()
            terminal.awaitText("Dispatch UI Sample")
            val baselineHeap = collectPostGcHeap(terminal, "baseline")

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
            val heapSamples = Regex("\\\"heapUsedBytes\\\":(\\d+)").findAll(diagnostics).map { it.groupValues[1].toLong() }.toList()
            assertTrue(heapSamples.size >= 3, "Expected periodic in-process heap samples")
            assertTrue(diagnostics.contains("\"event\":\"render_frame\""))
            assertTrue(diagnostics.contains("\"event\":\"terminal_write\""))
            assertTrue(
                finalHeap <= baselineHeap + MAX_RETAINED_HEAP_GROWTH_BYTES,
                "Retained heap grew from $baselineHeap to $finalHeap after bounded route churn",
            )
        }
    }

    private fun collectPostGcHeap(terminal: PtyTerminalSession, label: String): Long {
        terminal.requestGarbageCollection()
        Thread.sleep(650)
        val start = terminal.checkpoint()
        terminal.sendCtrlP()
        terminal.awaitText("Go to", after = start)
        terminal.sendEscape()
        terminal.awaitQuiet(period = Duration.ofMillis(150), timeout = Duration.ofSeconds(3))
        val heap = requireNotNull(terminal.latestHeapUsedBytes()) { "Missing post-GC heap sample for $label" }
        terminal.recordMeasurement("${label}HeapUsedBytes", heap)
        return heap
    }

    private companion object {
        const val MAX_RETAINED_HEAP_GROWTH_BYTES = 20L * 1024 * 1024
    }
}
