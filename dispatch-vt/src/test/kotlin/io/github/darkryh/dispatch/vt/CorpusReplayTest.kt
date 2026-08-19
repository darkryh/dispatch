package io.github.darkryh.dispatch.vt

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays every recorded PTY capture through the screen model and judges what the screen showed.
 *
 * These captures were produced by `dispatch-sample`'s PTY suite and, until this module existed,
 * were written to `build/reports/` and never read. The same runs that reported
 * `blinkCandidates: 0` are the input here.
 *
 * If the corpus is absent the tests skip rather than fail, so a clean checkout stays green; run
 * `./gradlew :dispatch-sample:terminalE2eTest` to populate it.
 */
class CorpusReplayTest {
    private fun scenarios(): List<CorpusScenario> = Corpus.scenarios()

    private fun analyze(scenario: CorpusScenario): FlickerReport =
        FlickerAnalyzer(scenario.width, scenario.height).analyze(scenario.name, scenario.frames())

    @Test
    fun `the corpus exists when it is required to`() {
        // Every invariant below returns early when there is no recording, so a clean checkout stays
        // green. That is convenient and dangerous: with an empty corpus the whole suite passes
        // vacuously. CI sets -Ddispatch.vt.requireCorpus=true after running the PTY suite, which
        // turns "nothing to judge" into a failure instead of a false green.
        if (System.getProperty("dispatch.vt.requireCorpus") != "true") return
        val scenarios = scenarios()
        assertTrue(
            scenarios.isNotEmpty(),
            "no recordings found under ${Corpus.directory()} — run :dispatch-sample:terminalE2eTest first",
        )
    }

    @Test
    fun `frame segmentation reproduces the application's own write count`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return

        // The app logs one `terminal_write` per flushBuffer call. Segmenting the chunk timeline at a
        // 60 Hz gap must recover the same number of frames, or the segmentation is fiction and every
        // finding built on it is worthless.
        val mismatches = mutableListOf<String>()
        for (scenario in scenarios) {
            val expected = scenario.terminalWrites
            if (expected == 0) continue
            val actual = scenario.frames().size
            val drift = kotlin.math.abs(actual - expected).toDouble() / expected
            if (drift > SEGMENTATION_TOLERANCE) {
                mismatches += "${scenario.name}: segmented $actual frames, app logged $expected renders"
            }
        }
        assertTrue(
            mismatches.isEmpty(),
            "frame segmentation does not track the app's own render count:\n" +
                mismatches.joinToString("\n"),
        )
    }

    @Test
    fun `recordings are newer than the renderer they describe`() {
        val scenarios = Corpus.directory() ?: return
        val rendererClasses =
            java.io.File(scenarios.parentFile.parentFile.parentFile.parentFile, "dispatch-renderer/build/classes")
        if (!rendererClasses.isDirectory) return
        val rendererBuiltAt = rendererClasses.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() }
            ?: return

        // A recording produced before the current renderer was compiled describes code that no
        // longer exists, and every verdict drawn from it is fiction. This is not hypothetical: a
        // stale capture left behind by an unrelated run silently produced phantom failures here.
        val stale =
            scenarios
                .listFiles { file: java.io.File -> file.isDirectory }
                .orEmpty()
                .filter { java.io.File(it, "terminal-chunks.tsv").lastModified() < rendererBuiltAt }
                .map { it.name }
        assertTrue(
            stale.isEmpty(),
            "stale recording(s) predate the current renderer build — re-run " +
                ":dispatch-sample:terminalE2eTest: $stale",
        )
    }

    @Test
    fun `report what the screen actually showed`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) {
            println("[dispatch-vt] no corpus found; run :dispatch-sample:terminalE2eTest first")
            return
        }
        val reports = scenarios.map(::analyze)
        println(buildString {
            appendLine()
            appendLine("=".repeat(78))
            appendLine("DISPATCH RENDER TRACE — replayed through a terminal model")
            appendLine("=".repeat(78))
            appendLine(
                "%-34s %6s %6s %7s %8s %7s".format(
                    "scenario", "frames", "whole", "damage", "clears", "I1",
                ),
            )
            for (report in reports) {
                appendLine(
                    "%-34s %6d %6d %6.1fx %8d %7d".format(
                        report.scenario,
                        report.stats.frames,
                        report.stats.framesDeliveredWhole,
                        report.stats.damageRatio,
                        report.stats.scrollbackClears,
                        report.findings("I1").size,
                    ),
                )
            }
            appendLine()
            for (report in reports) {
                if (report.findings.isNotEmpty()) appendLine(report.summary())
            }
        })
    }

    @Test
    fun `I1 - no frame presents a blank screen between two painted states`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        val violations = scenarios.map(::analyze).flatMap { it.findings("I1") }
        if (violations.isEmpty()) return
        fail(
            buildString {
                appendLine("${violations.size} blank presentation(s) — the user saw the screen blink:")
                violations.take(REPORTED).forEach { appendLine("  ${it.message}") }
                if (violations.size > REPORTED) appendLine("  ... ${violations.size - REPORTED} more")
            },
        )
    }

    @Test
    fun `I6 - every frame reaches the terminal as one write or is synchronized`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        val violations = scenarios.map(::analyze).flatMap { it.findings("I6") }
        if (violations.isEmpty()) return
        fail(
            buildString {
                appendLine("${violations.size} frame(s) split across multiple writes without a sync guard:")
                violations.take(REPORTED).forEach { appendLine("  ${it.message}") }
                if (violations.size > REPORTED) appendLine("  ... ${violations.size - REPORTED} more")
            },
        )
    }

    @Test
    fun `I2 - incremental frames stay proportional to what changed`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        // Frames that erase the screen are exempt (see FlickerAnalyzer.checkDamage) — a declared
        // full repaint is not amplification. What is left are frames that repaint the whole
        // viewport without erasing, because the diff cache was dropped by an intervening
        // appendScrollingContent or clearActiveArea. That is a real, known remainder: it is
        // ratcheted here rather than hidden, so it cannot quietly get worse.
        val violations = scenarios.map(::analyze).flatMap { it.findings("I2") }
        if (violations.size <= MAX_OVER_DAMAGED_FRAMES) return
        fail(
            buildString {
                appendLine(
                    "${violations.size} over-damaged frame(s), baseline $MAX_OVER_DAMAGED_FRAMES:",
                )
                violations.take(REPORTED).forEach { appendLine("  ${it.message}") }
                if (violations.size > REPORTED) appendLine("  ... ${violations.size - REPORTED} more")
            },
        )
    }

    @Test
    fun `I3 - native scrolling is never taken away from the terminal`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        val violations = scenarios.map(::analyze).flatMap { it.findings("I3") }
        assertTrue(
            violations.isEmpty(),
            "alternate screen or scroll regions used:\n" + violations.joinToString("\n") { "  ${it.message}" },
        )
    }

    @Test
    fun `I3b - scrollback is destroyed only for a declared reason`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        // Wiping scrollback on navigation is deliberate: the previous screen must not sit above the
        // new one when the user scrolls back. Anything OUTSIDE this set is destroying the user's
        // history as a side effect. `terminal_resize` used to be in here and was removed — a resize
        // needs the visible screen erased, not the history deleted.
        val undeclared = mutableListOf<String>()
        for (scenario in scenarios) {
            val offenders =
                scenario.renderDecisions
                    .filter { it.clearScrollback && it.reason !in DECLARED_SCROLLBACK_WIPES }
                    .groupingBy { it.reason }
                    .eachCount()
            if (offenders.isNotEmpty()) {
                undeclared += "${scenario.name}: ${offenders.entries.joinToString { "${it.key}=${it.value}" }}"
            }
        }
        assertTrue(
            undeclared.isEmpty(),
            "scrollback destroyed for undeclared reasons:\n" + undeclared.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `I3b ratchet - the over-broad content-reset wipe is not spreading`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        // `scrolling_content_reset` fires on ANY shrink of the scrolling region, so a list that
        // filters by one row deletes history just as surely as clearing a chat does. Narrowing it by
        // shape is wrong — clearing the sample chat leaves 9 header rows, not 0, and the e2e suite
        // correctly rejects that. The real fix is a declarative policy: a screen or widget states
        // that it resets history. Until that exists, this holds the line.
        val count =
            scenarios.sumOf { scenario ->
                scenario.renderDecisions.count {
                    it.clearScrollback && it.reason == "scrolling_content_reset"
                }
            }
        assertTrue(
            count <= MAX_CONTENT_RESET_WIPES,
            "incidental scrollback wipes rose to $count (baseline $MAX_CONTENT_RESET_WIPES)",
        )
    }

    @Test
    fun `the renderer's escape vocabulary is closed`() {
        val scenarios = scenarios()
        if (scenarios.isEmpty()) return
        val unknown = scenarios.map(::analyze).flatMap { it.stats.unknownSequences }.distinct()
        assertTrue(
            unknown.isEmpty(),
            "the model met sequences it does not implement, so its verdicts are not trustworthy: $unknown",
        )
    }

    private companion object {
        const val REPORTED = 12
        const val SEGMENTATION_TOLERANCE = 0.25

        /** Reasons that are allowed to destroy the terminal's scrollback. */
        val DECLARED_SCROLLBACK_WIPES = setOf("screen_transition", "scrolling_content_reset")

        // Ratchets for known, named remainders. Lower them as the underlying issues are fixed;
        // never raise them without saying why.
        // Measured at 14 and 17 respectively after the render fixes landed; the headroom absorbs
        // run-to-run timing variance in the PTY scenarios. Lower these as the underlying issues are
        // fixed. Never raise one without recording why.
        //
        // History: over-damaged frames were 95 before the viewport diff, 59 after it, and 14 once
        // updateActiveArea stopped repainting every sibling on any change.
        const val MAX_OVER_DAMAGED_FRAMES = 25
        const val MAX_CONTENT_RESET_WIPES = 25
    }
}
