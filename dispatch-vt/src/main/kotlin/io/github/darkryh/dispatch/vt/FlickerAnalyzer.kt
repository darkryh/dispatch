package io.github.darkryh.dispatch.vt

/** One slice of terminal output as it left the process, with the time it was observed. */
data class OutputChunk(
    val text: String,
    val elapsedNanos: Long = 0L,
)

/**
 * One logical frame: everything the renderer emitted for a single repaint.
 *
 * A frame may consist of several [chunks] — that is the defect this whole module exists to measure.
 * Each boundary between chunks is a point at which a real terminal could have painted.
 */
data class Frame(
    val index: Int,
    val chunks: List<OutputChunk>,
) {
    val text: String get() = chunks.joinToString("") { it.text }
    val byteSize: Int get() = text.toByteArray(Charsets.UTF_8).size

    /** Points at which the terminal could present a partially-applied frame. */
    val presentationPoints: Int get() = chunks.size
}

enum class Severity { CRITICAL, WARNING, INFO }

/** A single invariant violation, tied to the frame and screen location that produced it. */
data class Finding(
    val invariant: String,
    val severity: Severity,
    val frameIndex: Int,
    val message: String,
)

/** Aggregate measurements for a whole trace. */
data class TraceStats(
    val frames: Int,
    val framesDeliveredWhole: Int,
    val maxPresentationPoints: Int,
    val maxIntraFrameGapNanos: Long,
    val totalRowsTouched: Int,
    val totalRowsChanged: Int,
    val scrollbackClears: Int,
    val altScreenUses: Int,
    val scrollRegionUses: Int,
    val synchronizedFrames: Int,
    val implicitWraps: Int,
    val unknownSequences: List<String>,
) {
    /** Rows rewritten per row that actually needed to change. 1.0 is perfect. */
    val damageRatio: Double
        get() = if (totalRowsChanged == 0) 0.0 else totalRowsTouched.toDouble() / totalRowsChanged
}

data class FlickerReport(
    val scenario: String,
    val stats: TraceStats,
    val findings: List<Finding>,
) {
    fun findings(invariant: String): List<Finding> = findings.filter { it.invariant == invariant }

    val critical: List<Finding> get() = findings.filter { it.severity == Severity.CRITICAL }

    fun summary(): String =
        buildString {
            appendLine("=== $scenario ===")
            appendLine(
                "frames=${stats.frames} whole=${stats.framesDeliveredWhole} " +
                    "maxPoints=${stats.maxPresentationPoints} " +
                    "maxGap=${stats.maxIntraFrameGapNanos / 1_000_000}ms",
            )
            appendLine(
                "rowsTouched=${stats.totalRowsTouched} rowsChanged=${stats.totalRowsChanged} " +
                    "damageRatio=${"%.1f".format(stats.damageRatio)}x",
            )
            appendLine(
                "scrollbackClears=${stats.scrollbackClears} altScreen=${stats.altScreenUses} " +
                    "scrollRegion=${stats.scrollRegionUses} syncFrames=${stats.synchronizedFrames}",
            )
            val byInvariant = findings.groupBy { it.invariant }.toSortedMap()
            for ((invariant, list) in byInvariant) {
                appendLine("  $invariant: ${list.size} (${list.first().severity})")
                list.take(MAX_EXAMPLES).forEach { appendLine("      ${it.message}") }
                if (list.size > MAX_EXAMPLES) appendLine("      ... ${list.size - MAX_EXAMPLES} more")
            }
        }

    private companion object {
        const val MAX_EXAMPLES = 3
    }
}

/** Thresholds the analyzer judges against. */
data class FlickerBudget(
    /** Rows a frame may rewrite per row that genuinely changed, before it counts as over-damage. */
    val maxDamageRatio: Double = 3.0,
    /** Below this many changed rows, damage ratio is noisy and not worth reporting. */
    val minChangedRowsForDamage: Int = 1,
    /** Frames are expected to reach the terminal in a single write. */
    val maxPresentationPoints: Int = 1,
)

/**
 * Replays a stream of terminal output through a [VtScreen] and reports what the screen showed.
 *
 * The core idea: snapshot the screen at every point a terminal could have painted — that is, at
 * every chunk boundary — and compare those intermediate states against the frame's own start and
 * end. If a row is painted before the frame and painted after it, but blank in between, the user
 * saw it blink. No assertion on the byte stream can express that; this one can.
 *
 * A frame wrapped in DEC 2026 synchronized output is exempt from the blank-presentation and
 * presentation-point checks, because the terminal is then contractually obliged to buffer the whole
 * update. That exemption is how the fix proves itself.
 */
class FlickerAnalyzer(
    private val width: Int,
    private val height: Int,
    private val budget: FlickerBudget = FlickerBudget(),
) {
    fun analyze(scenario: String, frames: List<Frame>): FlickerReport {
        val screen = VtScreen(width, height)
        val parser = VtParser(screen)
        val findings = mutableListOf<Finding>()

        var framesWhole = 0
        var maxPoints = 0
        var maxGap = 0L
        var rowsTouched = 0
        var rowsChanged = 0
        var syncFrames = 0

        for (frame in frames) {
            val before = screen.snapshot()
            screen.resetDamage()

            val opsBefore = parser.operations.size
            val intermediates = mutableListOf<ScreenSnapshot>()
            for (chunk in frame.chunks) {
                parser.feed(chunk.text)
                intermediates += screen.snapshot()
            }
            val after = screen.snapshot()
            val frameOps = parser.operations.drop(opsBefore)
            val synchronized = isSynchronized(frameOps)
            if (synchronized) syncFrames += 1

            maxPoints = maxOf(maxPoints, frame.presentationPoints)
            maxGap = maxOf(maxGap, intraFrameGap(frame))
            if (frame.presentationPoints <= budget.maxPresentationPoints || synchronized) framesWhole += 1

            rowsTouched += screen.touchedRows.size
            val changed = after.changedRows(before)
            rowsChanged += changed.size

            findings += checkBlankPresentation(frame, before, after, intermediates, synchronized)
            findings += checkAtomicity(frame, synchronized)
            val erasedScreen =
                frameOps.any {
                    it.kind == VtOp.Kind.ERASE_DISPLAY || it.kind == VtOp.Kind.ERASE_SCROLLBACK
                }
            findings += checkDamage(frame, screen.touchedRows.size, changed.size, erasedScreen)
            findings += checkNativeScroll(frame, frameOps)
            findings += checkCursor(frame, intermediates)
            findings += checkWrap(frame, screen.implicitWraps)
            screen.resetDamage()
        }

        val stats =
            TraceStats(
                frames = frames.size,
                framesDeliveredWhole = framesWhole,
                maxPresentationPoints = maxPoints,
                maxIntraFrameGapNanos = maxGap,
                totalRowsTouched = rowsTouched,
                totalRowsChanged = rowsChanged,
                scrollbackClears = parser.operations.count { it.kind == VtOp.Kind.ERASE_SCROLLBACK },
                altScreenUses = parser.operations.count { it.kind == VtOp.Kind.ALT_SCREEN_ENTER },
                scrollRegionUses = parser.operations.count { it.kind == VtOp.Kind.SCROLL_REGION_SET },
                synchronizedFrames = syncFrames,
                implicitWraps = screen.implicitWraps,
                unknownSequences = parser.unknownSequences.distinct(),
            )
        return FlickerReport(scenario, stats, findings)
    }

    // -------------------------------------------------------------- invariants

    /** I1 — no row that is painted before and after a frame may be blank during it. */
    private fun checkBlankPresentation(
        frame: Frame,
        before: ScreenSnapshot,
        after: ScreenSnapshot,
        intermediates: List<ScreenSnapshot>,
        synchronized: Boolean,
    ): List<Finding> {
        if (synchronized || intermediates.size <= 1) return emptyList()
        val findings = mutableListOf<Finding>()
        // The last snapshot is the settled frame, not an intermediate presentation.
        for ((pointIndex, snapshot) in intermediates.dropLast(1).withIndex()) {
            val flashed =
                (0 until height).filter { row ->
                    !before.isRowBlank(row) && !after.isRowBlank(row) && snapshot.isRowBlank(row)
                }
            if (flashed.isEmpty()) continue
            val whole = flashed.size == height
            findings +=
                Finding(
                    invariant = "I1",
                    severity = Severity.CRITICAL,
                    frameIndex = frame.index,
                    message =
                        "frame ${frame.index} point ${pointIndex + 1}/${intermediates.size}: " +
                            (if (whole) "ENTIRE SCREEN blank" else "${flashed.size} row(s) blank") +
                            " (rows ${flashed.take(6).joinToString(",")}" +
                            (if (flashed.size > 6) ",…" else "") + ") between painted states",
                )
        }
        return findings
    }

    /** I6 — a frame should reach the terminal as one write, or be explicitly synchronized. */
    private fun checkAtomicity(frame: Frame, synchronized: Boolean): List<Finding> {
        if (synchronized || frame.presentationPoints <= budget.maxPresentationPoints) return emptyList()
        return listOf(
            Finding(
                invariant = "I6",
                severity = Severity.CRITICAL,
                frameIndex = frame.index,
                message =
                    "frame ${frame.index}: ${frame.byteSize} bytes delivered in " +
                        "${frame.presentationPoints} writes, unsynchronized",
            ),
        )
    }

    /**
     * I2 — rows rewritten must stay proportional to rows that actually changed.
     *
     * A frame that erases the screen is exempt. Erasing and repainting is what a declared full
     * repaint *is* — a screen transition, a content reset — and scoring it as amplification would
     * measure intent as a defect. This invariant targets incremental frames, where rewriting forty
     * rows to change one is pure waste.
     */
    private fun checkDamage(
        frame: Frame,
        touched: Int,
        changed: Int,
        erasedScreen: Boolean,
    ): List<Finding> {
        if (erasedScreen) return emptyList()
        if (changed < budget.minChangedRowsForDamage) return emptyList()
        val ratio = touched.toDouble() / changed
        if (ratio <= budget.maxDamageRatio) return emptyList()
        return listOf(
            Finding(
                invariant = "I2",
                severity = Severity.WARNING,
                frameIndex = frame.index,
                message =
                    "frame ${frame.index}: rewrote $touched row(s) to change $changed " +
                        "(${"%.0f".format(ratio)}x damage)",
            ),
        )
    }

    /** I3 — native terminal scrolling must stay in charge. */
    private fun checkNativeScroll(frame: Frame, ops: List<VtOp>): List<Finding> =
        ops.mapNotNull { op ->
            when (op.kind) {
                VtOp.Kind.ALT_SCREEN_ENTER ->
                    Finding(
                        "I3",
                        Severity.CRITICAL,
                        frame.index,
                        "frame ${frame.index}: entered the alternate screen buffer (${op.detail}) — " +
                            "this destroys native scrolling",
                    )
                VtOp.Kind.SCROLL_REGION_SET ->
                    Finding(
                        "I3",
                        Severity.CRITICAL,
                        frame.index,
                        "frame ${frame.index}: set a DECSTBM scroll region (${op.detail}) — " +
                            "this fights native scrolling",
                    )
                else -> null
            }
        }

    /** I7 — the cursor must stay hidden for the whole session once the UI owns the screen. */
    private fun checkCursor(frame: Frame, intermediates: List<ScreenSnapshot>): List<Finding> {
        if (frame.index == 0) return emptyList()
        val visible = intermediates.count { it.cursorVisible }
        if (visible == 0) return emptyList()
        return listOf(
            Finding(
                "I7",
                Severity.WARNING,
                frame.index,
                "frame ${frame.index}: cursor visible during repaint",
            ),
        )
    }

    /** I9 — content must never wrap implicitly; the layout engine owns row width. */
    private fun checkWrap(frame: Frame, implicitWraps: Int): List<Finding> {
        if (implicitWraps == 0) return emptyList()
        return listOf(
            Finding(
                "I9",
                Severity.WARNING,
                frame.index,
                "frame ${frame.index}: $implicitWraps implicit wrap(s) — a row exceeded the terminal width",
            ),
        )
    }

    private companion object {
        fun isSynchronized(ops: List<VtOp>): Boolean {
            val begin = ops.indexOfFirst { it.kind == VtOp.Kind.SYNC_BEGIN }
            val end = ops.indexOfLast { it.kind == VtOp.Kind.SYNC_END }
            return begin >= 0 && end > begin
        }

        fun intraFrameGap(frame: Frame): Long {
            if (frame.chunks.size < 2) return 0L
            var maxGap = 0L
            for (i in 1 until frame.chunks.size) {
                val gap = frame.chunks[i].elapsedNanos - frame.chunks[i - 1].elapsedNanos
                if (gap > maxGap) maxGap = gap
            }
            return maxGap
        }
    }
}
