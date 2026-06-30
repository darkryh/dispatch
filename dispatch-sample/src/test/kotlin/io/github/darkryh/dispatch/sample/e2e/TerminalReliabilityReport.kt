package io.github.darkryh.dispatch.sample.e2e

import java.nio.file.Files
import java.nio.file.Path

internal data class TerminalReliabilityReport(
    val scenario: String,
    val renderFrames: Int,
    val terminalWrites: Int,
    val fullRewrites: Int,
    val unexpectedFullRewrites: Int,
    val clearScreenWrites: Int,
    val maximumHeapUsedBytes: Long,
    val firstRssKb: Long,
    val maximumRssKb: Long,
    val lastRssKb: Long,
    val cursorHideCount: Int,
    val cursorShowCount: Int,
    val rawClearScreenSequences: Int,
) {
    val blinkCandidates: Int get() = maxOf(clearScreenWrites, rawClearScreenSequences)

    fun toText(): String =
        buildString {
            appendLine("scenario=$scenario")
            appendLine("renderFrames=$renderFrames terminalWrites=$terminalWrites")
            appendLine("fullRewrites=$fullRewrites unexpectedFullRewrites=$unexpectedFullRewrites")
            appendLine("clearScreenWrites=$clearScreenWrites rawClearScreenSequences=$rawClearScreenSequences")
            appendLine("blinkCandidates=$blinkCandidates")
            appendLine("heapPeakBytes=$maximumHeapUsedBytes")
            appendLine("rssFirstKb=$firstRssKb rssPeakKb=$maximumRssKb rssLastKb=$lastRssKb")
            appendLine("cursorHideCount=$cursorHideCount cursorShowCount=$cursorShowCount")
        }

    fun toJson(): String =
        """{"scenario":"${scenario.jsonEscape()}","renderFrames":$renderFrames,"terminalWrites":$terminalWrites,"fullRewrites":$fullRewrites,"unexpectedFullRewrites":$unexpectedFullRewrites,"clearScreenWrites":$clearScreenWrites,"blinkCandidates":$blinkCandidates,"maximumHeapUsedBytes":$maximumHeapUsedBytes,"firstRssKb":$firstRssKb,"maximumRssKb":$maximumRssKb,"lastRssKb":$lastRssKb,"cursorHideCount":$cursorHideCount,"cursorShowCount":$cursorShowCount,"rawClearScreenSequences":$rawClearScreenSequences}\n"""

    companion object {
        fun analyze(
            scenario: String,
            raw: String,
            diagnosticsFile: Path,
            rssSamples: List<RssSample>,
        ): TerminalReliabilityReport {
            val diagnostics = if (Files.exists(diagnosticsFile)) Files.readAllLines(diagnosticsFile) else emptyList()
            val decisions = diagnostics.filter { it.contains("\"event\":\"render_decision\"") }
            val full = decisions.filter { it.contains("\"kind\":\"FULL_REWRITE\"") }
            val expectedReasons =
                setOf(
                    "initial_frame",
                    "terminal_resize",
                    "non_prefix_scrolling_change",
                    "active_to_scrolling_boundary_shift",
                    "screen_transition",
                    "scrolling_content_reset",
                    "structural_scrolling_growth",
                )
            val unexpected = full.count { line -> expectedReasons.none { line.contains("\"reason\":\"$it\"") } }
            val heapValues =
                diagnostics.mapNotNull {
                    HEAP_REGEX
                        .find(it)
                        ?.groupValues
                        ?.get(1)
                        ?.toLongOrNull()
                }
            val writes = diagnostics.filter { it.contains("\"event\":\"terminal_write\"") }
            val rss = rssSamples.map(RssSample::rssKb)
            return TerminalReliabilityReport(
                scenario = scenario,
                renderFrames = diagnostics.count { it.contains("\"event\":\"render_frame\"") },
                terminalWrites = writes.size,
                fullRewrites = full.size,
                unexpectedFullRewrites = unexpected,
                clearScreenWrites = writes.count { it.contains("\"clearScreen\":true") },
                maximumHeapUsedBytes = heapValues.maxOrNull() ?: 0,
                firstRssKb = rss.firstOrNull() ?: 0,
                maximumRssKb = rss.maxOrNull() ?: 0,
                lastRssKb = rss.lastOrNull() ?: 0,
                cursorHideCount = diagnostics.count { it.contains("\"event\":\"cursor\"") && it.contains("\"visible\":false") },
                cursorShowCount = diagnostics.count { it.contains("\"event\":\"cursor\"") && it.contains("\"visible\":true") },
                rawClearScreenSequences = raw.countOccurrences("\u001B[2J"),
            )
        }

        private val HEAP_REGEX = Regex("\"heapUsedBytes\":(\\d+)")
    }
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private fun String.countOccurrences(needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var offset = 0
    while (offset <= length - needle.length) {
        val match = indexOf(needle, startIndex = offset)
        if (match < 0) break
        count++
        offset = match + needle.length
    }
    return count
}
