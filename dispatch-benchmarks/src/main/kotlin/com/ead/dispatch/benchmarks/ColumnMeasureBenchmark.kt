package com.ead.dispatch.benchmarks

import com.ead.dispatch.constraints.Constraints
import com.ead.dispatch.layout.Alignment
import com.ead.dispatch.layout.Arrangement
import com.ead.dispatch.layout.Measurable
import com.ead.dispatch.layout.MeasurePolicy
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
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
 * Benchmarks `ColumnMeasurePolicy.measure` + its placement block.
 *
 * Proves the FloatArray weight resolution avoids `Pair`/`Float` boxing. The policy is `internal`, so
 * it is constructed via reflection in `@Setup` — but driven through the PUBLIC [MeasurePolicy]
 * interface in the hot loop. `maxHeight` is bounded so `hasBoundedHeight` is true and the weight path
 * engages. Note the residual `sizes.toList()` allocation in the policy when checking `-prof gc`.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
open class ColumnMeasureBenchmark {

    @Param("8", "64", "256")
    var children: Int = 0

    @Param("NONE", "ALL", "HALF")
    var weighting: String = "NONE"

    private var policy: MeasurePolicy? = null
    private var measurables: List<Measurable> = emptyList()
    private var constraints: Constraints = Constraints.Unbounded
    private val scope = NoOpPlacementScope()

    @Setup
    fun setUp() {
        // VERIFY: internal ColumnMeasurePolicy(Arrangement.Vertical, Alignment.Horizontal) in dispatch-layout.
        val cls = Class.forName("com.ead.dispatch.layout.ColumnMeasurePolicy")
        val ctor =
            cls.getDeclaredConstructor(Arrangement.Vertical::class.java, Alignment.Horizontal::class.java)
                .apply { isAccessible = true }
        policy = ctor.newInstance(Arrangement.Top, Alignment.Start) as MeasurePolicy

        measurables = buildMeasurables(children, Weighting.valueOf(weighting), childWidth = 1, childHeight = 1)
        // Bounded maxHeight -> weights engage; bounded maxWidth keeps layout width finite.
        constraints = Constraints(minWidth = 0, maxWidth = 200, minHeight = 0, maxHeight = 1000)
    }

    @Benchmark
    fun measure(bh: Blackhole) {
        val result = policy!!.measure(measurables, constraints)
        result.placementBlock(scope)
        bh.consume(result)
        bh.consume(scope.sink)
    }
}
