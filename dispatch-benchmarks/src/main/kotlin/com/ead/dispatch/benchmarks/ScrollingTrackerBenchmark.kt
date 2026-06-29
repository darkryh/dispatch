package com.ead.dispatch.benchmarks

import java.lang.reflect.Method
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
 * Benchmarks `ScrollingContentTracker.consume` — the append-only history diff.
 *
 * Proves the O(total) -> O(appended) win: with a committed prefix of `history` lines and a frame that
 * grows by `delta` lines, `consume` walks only the new tail. Time should be **flat across `history`**
 * and **scale with `delta`**; steady-state allocation should be O(delta), not O(history).
 *
 * The tracker is `internal`, so it is constructed and its methods are bound via reflection in
 * `@Setup` (Trial). `@Setup(Level.Invocation)` re-seeds the committed lines to exactly `history`
 * before every measured call: without re-seeding, the second invocation would see
 * `scrollingLines.size == committedLines.size` and hit the early `ScrollUpdate.none()` return.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class ScrollingTrackerBenchmark {

    @Param("1000", "10000", "100000")
    var history: Int = 0

    @Param("1", "20")
    var delta: Int = 0

    private var tracker: Any? = null
    private var consumeMethod: Method? = null
    private var syncMethod: Method? = null

    // Stable input lists, allocated once. `fullLines` shares `baseLines` as its prefix so
    // `consume` takes the startsWithCommitted -> append-tail branch.
    private var baseLines: List<String> = emptyList()
    private var fullLines: List<String> = emptyList()

    @Setup(Level.Trial)
    fun setUp() {
        // VERIFY: fully-qualified internal class name in dispatch-core.
        val cls = Class.forName("com.ead.dispatch.runtime.ScrollingContentTracker")
        val ctor = cls.getDeclaredConstructor().apply { isAccessible = true }
        tracker = ctor.newInstance()
        consumeMethod = cls.getDeclaredMethod("consume", List::class.java).apply { isAccessible = true }
        syncMethod = cls.getDeclaredMethod("sync", List::class.java).apply { isAccessible = true }

        val base = ArrayList<String>(history)
        for (i in 0 until history) base.add("line-$i")
        baseLines = base

        val full = ArrayList<String>(history + delta)
        full.addAll(base)
        for (i in 0 until delta) full.add("tail-$i")
        fullLines = full
    }

    /**
     * Re-seed committedLines to the `history` prefix so each measured `consume` appends exactly
     * `delta` lines instead of short-circuiting on an equal-size frame.
     */
    @Setup(Level.Invocation)
    fun reseed() {
        syncMethod!!.invoke(tracker, baseLines)
    }

    @Benchmark
    fun appendTail(bh: Blackhole) {
        // Returns an internal ScrollUpdate; sunk as Object to defeat DCE.
        bh.consume(consumeMethod!!.invoke(tracker, fullLines))
    }
}
