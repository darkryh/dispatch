package com.ead.dispatch.benchmarks

import com.ead.dispatch.render.RenderDiagnostics
import com.ead.dispatch.render.TerminalRenderer
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole

/**
 * Benchmarks `TerminalRenderer` flush paths against a recording terminal.
 *
 * `TerminalRenderer` reuses a single `scratchBuffer` and emits exactly one `rawPrint` per call. The
 * benchmarks should show flat allocation across `lineCount`/`width`.
 *
 * - [appendScrolling] drives the append-only path (clear active area, print lines, restore).
 * - [activeAreaToggle] alternates two DISTINCT active-area frames so `updateActiveArea` never hits
 *   its "content unchanged" early return and actually repaints each call.
 * - [diagnosticsState] is the diagnostics on/off variant note: `flushBuffer` scans/copies the frame
 *   (`buffer.toString().toByteArray()`) only when `RenderDiagnostics.isEnabled`, which is gated by
 *   the `DISPATCH_DIAGNOSTICS_FILE` env var. With it UNSET (the default measured here) the renderer
 *   sheds that work; set the env var before running to benchmark the on-path. The flag is sunk so the
 *   branch is observable in the result without depending on env state.
 *
 * The [TerminalRecorder] output buffer is cleared once per iteration to bound its growth; this is
 * outside the measured method.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class TerminalRendererBenchmark {

    @Param("1", "10", "50")
    var lineCount: Int = 0

    @Param("40", "200")
    var width: Int = 0

    private var recorder: TerminalRecorder? = null
    private var renderer: TerminalRenderer = TerminalRenderer(Terminal())

    private var scrolling: List<String> = emptyList()
    private var frameA: List<String> = emptyList()
    private var frameB: List<String> = emptyList()
    private var toggle = false

    @Setup(Level.Trial)
    fun setUp() {
        val rec =
            TerminalRecorder(
                ansiLevel = AnsiLevel.TRUECOLOR,
                width = width,
                height = 24,
                supportsAnsiCursor = true,
            )
        recorder = rec
        renderer = TerminalRenderer(Terminal(terminalInterface = rec))

        scrolling = List(lineCount) { "scroll-$it" }
        frameA = List(lineCount) { "A-$it" }
        frameB = List(lineCount) { "B-$it" }

        // Prime an active area so appendScrolling exercises clear+restore around the printed lines.
        renderer.updateActiveArea(frameA)
    }

    /** Bound the recorder's cumulative output buffer between iterations (outside measurement). */
    @Setup(Level.Iteration)
    fun clearRecorder() {
        recorder?.clearOutput()
    }

    @Benchmark
    fun appendScrolling(bh: Blackhole) {
        renderer.appendScrollingContent(scrolling)
        bh.consume(renderer)
    }

    @Benchmark
    fun activeAreaToggle(bh: Blackhole) {
        toggle = !toggle
        renderer.updateActiveArea(if (toggle) frameB else frameA)
        bh.consume(toggle)
    }

    @Benchmark
    fun appendScrollingDiagnosticsGated(bh: Blackhole) {
        renderer.appendScrollingContent(scrolling)
        bh.consume(RenderDiagnostics.isEnabled)
    }
}
