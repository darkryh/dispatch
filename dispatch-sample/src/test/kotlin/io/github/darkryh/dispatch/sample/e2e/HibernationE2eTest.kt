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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives the installed sample through a real PTY to prove idle hibernation end-to-end: the runtime
 * enters hibernation after [DISPATCH_SAMPLE_IDLE_TIMEOUT_MS] of silence and wakes on the next key.
 *
 * Assertions read the sidecar diagnostics JSON-lines (`hibernate_enter` / `hibernate_exit` /
 * `memory_sample`) the runtime emits when `DISPATCH_DIAGNOSTICS_FILE` is set — the harness wires that
 * file automatically. Timing-sensitive checks are kept tolerant because these run on real wall-clock.
 */
@Tag("terminal-e2e")
@EnabledOnOs(OS.MAC, OS.LINUX)
@Timeout(150)
class HibernationE2eTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `idle silence drives the runtime into hibernation`() {
        PtyTerminalSession
            .start(
                scenario = "hibernation-idle-enter",
                environment = mapOf(IDLE_TIMEOUT_ENV to SHORT_IDLE_MS.toString()),
            ).use { terminal ->
                openProgress(terminal)
                val diagnosticsStart = terminal.diagnosticCheckpoint()

                val enter = awaitDiagnosticEvent(terminal, "hibernate_enter", after = diagnosticsStart)
                assertTrue(
                    enter.contains("\"idleFps\":1"),
                    "hibernate_enter did not report the throttled idle FPS: $enter",
                )

                // Best-effort, tolerant memory check: releasing caches + requesting GC should not leave
                // the heap larger than before. A small slack absorbs allocator noise on real timing.
                val heapBefore = longField(enter, "heapBeforeBytes")
                val heapAfter = longField(enter, "heapAfterBytes")
                if (heapBefore != null && heapAfter != null) {
                    assertTrue(
                        heapAfter <= heapBefore + HEAP_SLACK_BYTES,
                        "Heap grew across hibernation: before=$heapBefore after=$heapAfter",
                    )
                }
            }
    }

    @Test
    fun `the next keystroke wakes the runtime quickly and repaints`() {
        PtyTerminalSession
            .start(
                scenario = "hibernation-wake-on-input",
                environment = mapOf(IDLE_TIMEOUT_ENV to SHORT_IDLE_MS.toString()),
            ).use { terminal ->
                openProgress(terminal)
                val enterStart = terminal.diagnosticCheckpoint()
                awaitDiagnosticEvent(terminal, "hibernate_enter", after = enterStart)

                val exitStart = terminal.diagnosticCheckpoint()
                val repaintAnchor = terminal.rawCheckpoint()
                terminal.sendSpace()

                val exit = awaitDiagnosticEvent(terminal, "hibernate_exit", after = exitStart)
                val wakeLatencyNanos = longField(exit, "wakeLatencyNanos")
                assertNotNull(wakeLatencyNanos, "hibernate_exit is missing wakeLatencyNanos: $exit")
                assertTrue(
                    wakeLatencyNanos < MAX_WAKE_LATENCY_NANOS,
                    "Wake latency ${wakeLatencyNanos}ns exceeded ${MAX_WAKE_LATENCY_NANOS}ns: $exit",
                )

                // The UI must repaint after waking: any byte written past the pre-key checkpoint proves it.
                terminal.awaitRawRegex(Regex("."), after = repaintAnchor, timeout = Duration.ofSeconds(5))
                terminal.awaitQuiet(period = Duration.ofMillis(200), timeout = Duration.ofSeconds(8))
            }
    }

    @Test
    fun `repeated hibernate and wake cycles do not leak`() {
        PtyTerminalSession
            .start(
                scenario = "hibernation-churn-leak-guard",
                environment = mapOf(IDLE_TIMEOUT_ENV to CHURN_IDLE_MS.toString()),
            ).use { terminal ->
                openProgress(terminal)

                // Settle once so the first memory sample reflects a steady screen, then baseline.
                awaitDiagnosticEvent(terminal, "hibernate_enter", after = terminal.diagnosticCheckpoint())
                terminal.sendSpace()
                awaitDiagnosticEvent(terminal, "hibernate_exit", after = terminal.diagnosticCheckpoint())
                terminal.awaitQuiet(period = Duration.ofMillis(150), timeout = Duration.ofSeconds(5))
                val baselineHeap = requireNotNull(terminal.latestHeapUsedBytes()) { "Missing baseline heap sample" }

                repeat(CHURN_CYCLES) { cycle ->
                    val enterStart = terminal.diagnosticCheckpoint()
                    awaitDiagnosticEvent(terminal, "hibernate_enter", after = enterStart)
                    val exitStart = terminal.diagnosticCheckpoint()
                    val repaintAnchor = terminal.rawCheckpoint()
                    terminal.sendSpace()
                    awaitDiagnosticEvent(terminal, "hibernate_exit", after = exitStart)
                    terminal.awaitRawRegex(Regex("."), after = repaintAnchor, timeout = Duration.ofSeconds(5))
                    terminal.recordMeasurement("churnCycle${cycle + 1}HeapBytes", terminal.latestHeapUsedBytes() ?: -1L)
                }

                terminal.awaitQuiet(period = Duration.ofMillis(150), timeout = Duration.ofSeconds(5))
                val finalHeap = requireNotNull(terminal.latestHeapUsedBytes()) { "Missing final heap sample" }
                terminal.recordMeasurement("churnHeapGrowthBytes", finalHeap - baselineHeap)
                assertTrue(
                    finalHeap <= baselineHeap + MAX_CHURN_HEAP_GROWTH_BYTES,
                    "Heap grew across hibernate/wake churn: baseline=$baselineHeap final=$finalHeap",
                )
            }
    }

    /**
     * Opens the animated Progress screen (continuous spinner/bar motion) so the idle FPS throttle has
     * something to act on. Mirrors the launcher-grid navigation used by the other PTY suites: Progress
     * is ordinal 6 (row 2, column 0), two Down presses from the top-left card.
     */
    private fun openProgress(terminal: PtyTerminalSession) {
        terminal.awaitText(HOME_TITLE)
        val start = terminal.checkpoint()
        repeat(2) { terminal.sendDown() }
        terminal.sendEnter()
        terminal.awaitText("$BANNER Progress", after = start)
    }

    /** Polls the diagnostics jsonl (from [after]) until a line carrying `"event":"<name>"` appears. */
    private fun awaitDiagnosticEvent(
        terminal: PtyTerminalSession,
        name: String,
        after: Long,
        timeout: Duration = Duration.ofSeconds(15),
    ): String {
        val needle = "\"event\":\"$name\""
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            terminal
                .diagnosticEvents(after)
                .lineSequence()
                .lastOrNull { it.contains(needle) }
                ?.let { return it }
            Thread.sleep(50)
        }
        error("Timed out after ${timeout.toMillis()} ms waiting for diagnostics event '$name'")
    }

    private fun longField(
        line: String,
        field: String,
    ): Long? =
        Regex("\"$field\":(-?\\d+)")
            .find(line)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()

    private companion object {
        const val IDLE_TIMEOUT_ENV = "DISPATCH_SAMPLE_IDLE_TIMEOUT_MS"

        /** Fast idle timeout for the single enter/wake scenarios. */
        const val SHORT_IDLE_MS = 1_500L

        /** Even shorter timeout to keep the multi-cycle churn test well under the suite timeout. */
        const val CHURN_IDLE_MS = 1_200L
        const val CHURN_CYCLES = 4

        /** Generous wake-latency ceiling (250ms) — wake is meant to be effectively instant. */
        const val MAX_WAKE_LATENCY_NANOS = 250_000_000L

        /** Slack absorbing allocator noise on the best-effort heap-shrink check. */
        const val HEAP_SLACK_BYTES = 4L * 1024 * 1024

        /** Tolerant bound on retained heap growth across the churn loop. */
        const val MAX_CHURN_HEAP_GROWTH_BYTES = 24L * 1024 * 1024

        const val HOME_TITLE = "Dispatch — Terminal UI Showcase"
        const val BANNER = "▌"
    }
}
