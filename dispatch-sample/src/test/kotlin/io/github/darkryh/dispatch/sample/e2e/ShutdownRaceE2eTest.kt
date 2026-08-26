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

/**
 * Quitting must be silent, even when the application is at its busiest.
 *
 * The failure this exists for: shutdown runs on the `runBlocking` caller thread, while the
 * recomposer's applier and the frame scheduler run on the UI dispatcher. Tearing the composition
 * down before stopping those meant `Composition.dispose()` cleared the `LayoutNode` children on one
 * thread while a frame walked the same list on the other. Most of the time the dispose landed
 * between frames and nothing happened. When it landed inside one, the frame coroutine died with a
 * `ConcurrentModificationException` that — no handler being installed on the UI scope — reached the
 * default uncaught-exception handler and dumped a stack trace over the terminal exactly as it was
 * being handed back to the shell. Intermittent, invisible in a quiet app, and increasingly likely
 * the more the app has going on.
 *
 * So this quits repeatedly with a stream mid-flight — the state churn is what widens the window —
 * and fails if any attempt printed a stack trace. A single clean run proves nothing about a race,
 * which is why it is [ATTEMPTS] of them.
 */
@Tag("terminal-e2e")
@EnabledOnOs(OS.MAC, OS.LINUX)
@Timeout(900)
class ShutdownRaceE2eTest {
    @BeforeEach
    fun requirePtyLauncher() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/script")), "PTY launcher /usr/bin/script is unavailable")
    }

    @Test
    fun `quitting a busy application never prints a stack trace`() {
        val failures = mutableListOf<String>()

        repeat(ATTEMPTS) { attempt ->
            PtyTerminalSession
                .start(
                    scenario = "shutdown-race-$attempt",
                    // A tall viewport with the whole of it in the active area is the shape that
                    // makes a frame expensive: every paint re-measures and rewrites the entire tree,
                    // so the window in which a frame is IN PROGRESS is as wide as it ever gets.
                    // This is the configuration a live dashboard runs (Katalyst's inspector included).
                    columns = 120,
                    lines = 50,
                    environment = KATALYST_SHAPED_ENVIRONMENT,
                ).use { terminal ->
                    terminal.awaitText(HOME_TITLE)
                    openBackgroundPolledScreen(terminal)

                    // Keys still arriving as the quit lands: selection repaints on the UI dispatcher
                    // overlap the teardown that runs on the caller thread.
                    repeat(KEY_BURST) {
                        terminal.sendDown()
                        Thread.sleep(KEY_REPEAT_MILLIS)
                    }
                    terminal.send(DOUBLE_CTRL_C)
                    terminal.awaitExit(Duration.ofSeconds(20))

                    stackTraceIn(terminal.plainTranscript())?.let { trace ->
                        failures += "attempt $attempt printed:\n$trace"
                    }
                }
        }

        assertTrue(
            failures.isEmpty(),
            "quitting must never print a stack trace — ${failures.size}/$ATTEMPTS attempts did:\n" +
                failures.joinToString("\n\n"),
        )
    }

    /**
     * Opens the screen that polls in the background on its own timer.
     *
     * That is what makes this different from quitting an idle app: the composition keeps changing
     * with no input at all, so the recomposer and the frame scheduler are both live at the instant
     * the exit key arrives — which is precisely the state a monitoring UI is always in.
     */
    private fun openBackgroundPolledScreen(terminal: PtyTerminalSession) {
        val start = terminal.checkpoint()
        terminal.sendCtrlP()
        terminal.awaitText(PALETTE_ANCHOR, after = start)
        repeat(POLLING_PALETTE_INDEX) { terminal.sendDown() }
        terminal.sendEnter()
        terminal.awaitText(POLLING_ANCHOR, after = start, timeout = Duration.ofSeconds(12))
        // Land inside a poll tick rather than neatly between two of them.
        Thread.sleep(MID_TICK_OFFSET_MILLIS)
    }

    /**
     * Returns the stack trace a run printed, or null if it exited cleanly.
     *
     * Matched on the `\tat some.package.Class(...)` frame shape rather than on exception class names:
     * the race can surface as any of `ConcurrentModificationException`,
     * `IndexOutOfBoundsException`, or whatever the Compose runtime raises when its slot table is
     * torn down under it, and naming them would let a new variant pass.
     */
    private fun stackTraceIn(output: String): String? {
        val lines = output.lines()
        val firstFrame = lines.indexOfFirst { STACK_FRAME.containsMatchIn(it) }
        if (firstFrame < 0) return null
        val from = (firstFrame - 4).coerceAtLeast(0)
        return lines.subList(from, (firstFrame + 12).coerceAtMost(lines.size)).joinToString("\n")
    }

    private companion object {
        /**
         * A race needs repetition to be evidence. Before the shutdown ordering was fixed this failed
         * within the first handful of attempts; it is kept high enough to keep catching a regression
         * and low enough to stay inside the suite's budget.
         */
        const val ATTEMPTS = 25

        const val HOME_TITLE = "Dispatch — Terminal UI Showcase"
        const val PALETTE_ANCHOR = "Chat — Live streaming"
        const val POLLING_ANCHOR = "Katalyst-shaped polling monitor"

        /** Down-presses from the palette's reset-to-top entry to the background-polled screen. */
        const val POLLING_PALETTE_INDEX = 12

        const val DOUBLE_CTRL_C = "\u0003\u0003"
        const val KEY_BURST = 6
        const val KEY_REPEAT_MILLIS = 30L
        const val MID_TICK_OFFSET_MILLIS = 900L

        /** `\tat package.Class.method(File.kt:42)` — the shape every JVM stack frame has. */
        val STACK_FRAME = Regex("""^\s+at [\w.$]+\.[\w$<>]+\(""")

        /** The render configuration of a full-viewport live dashboard. */
        val KATALYST_SHAPED_ENVIRONMENT =
            mapOf(
                "DISPATCH_SAMPLE_ACTIVE_AREA_HEIGHT" to "500",
                "DISPATCH_SAMPLE_TARGET_FPS" to "24",
                "DISPATCH_SAMPLE_IDLE_TIMEOUT_MS" to "300000",
            )
    }
}
